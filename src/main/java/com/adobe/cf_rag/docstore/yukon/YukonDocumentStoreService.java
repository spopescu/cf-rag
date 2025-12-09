package com.adobe.cf_rag.docstore.yukon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.adobe.cf_rag.docstore.api.DocumentStoreException;
import com.adobe.cf_rag.docstore.api.DocumentStoreService;
import com.adobe.cf_rag.docstore.api.model.Collection;
import com.adobe.cf_rag.docstore.api.model.InferenceResult;
import com.adobe.cf_rag.docstore.api.model.ListDocumentsResult;
import com.adobe.cf_rag.docstore.api.model.SearchResult;
import com.adobe.cf_rag.docstore.api.model.UploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Yukon-specific implementation of the DocumentStoreService.
 * Uses Adobe IMS for authentication and Yukon v2 API for document operations.
 */
public class YukonDocumentStoreService implements DocumentStoreService {

    private static final Logger LOG = LoggerFactory.getLogger(YukonDocumentStoreService.class);
    private static final String PROVIDER_NAME = "Yukon";

    private final YukonConfig config;
    private final ObjectMapper objectMapper;

    // Token caching
    private String cachedAccessToken;
    private long tokenExpirationTime;

    public YukonDocumentStoreService(YukonConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        LOG.info("YukonDocumentStoreService initialized with base URL: {}", config.getYukonBaseUrl());
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Collection createCollection(String name, String description) throws DocumentStoreException {
        try {
            String token = getAccessToken();
            return doCreateCollection(token, name, description);
        } catch (IOException e) {
            throw new DocumentStoreException("Failed to create collection: " + e.getMessage(), e);
        }
    }

    @Override
    public UploadResult uploadDocument(String collectionId, String fileName, String jsonContent)
            throws DocumentStoreException {
        try {
            String token = getAccessToken();
            return doUploadDocument(token, collectionId, fileName, jsonContent);
        } catch (IOException e) {
            throw new DocumentStoreException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Override
    public InferenceResult askQuestion(String collectionId, String question, List<String> documentIds)
            throws DocumentStoreException {
        try {
            String token = getAccessToken();
            return doAskQuestion(token, collectionId, question, documentIds);
        } catch (IOException e) {
            throw new DocumentStoreException("Failed to ask question: " + e.getMessage(), e);
        }
    }

    @Override
    public SearchResult searchDocuments(String collectionId, String query, int maxResults)
            throws DocumentStoreException {
        try {
            String token = getAccessToken();
            return doSearchDocuments(token, collectionId, query, maxResults);
        } catch (IOException e) {
            throw new DocumentStoreException("Failed to search documents: " + e.getMessage(), e);
        }
    }

    @Override
    public ListDocumentsResult listDocuments(String collectionId) throws DocumentStoreException {
        try {
            String token = getAccessToken();
            return doListDocuments(token, collectionId);
        } catch (IOException e) {
            throw new DocumentStoreException("Failed to list documents: " + e.getMessage(), e);
        }
    }

    // ========== Token Management ==========

    private synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && tokenExpirationTime > now + 300000) {
            return cachedAccessToken;
        }

        String tokenUrl = "https://" + config.getImsHost() + "/ims/token/v2";
        LOG.info("Exchanging authorization code for access token at: {}", tokenUrl);

        URL url = new URL(tokenUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "grant_type=authorization_code" +
                "&client_id=" + URLEncoder.encode(config.getClientId(), "UTF-8") +
                "&client_secret=" + URLEncoder.encode(config.getClientSecret(), "UTF-8") +
                "&code=" + URLEncoder.encode(config.getAuthorizationCode(), "UTF-8");

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes(body);
            out.flush();
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = readAll(is);
        conn.disconnect();

        if (status < 200 || status >= 300) {
            LOG.error("IMS token exchange failed: {}", responseBody);
            throw new IOException("IMS token exchange failed with HTTP " + status + ": " + responseBody);
        }

        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode accessTokenNode = json.get("access_token");
        if (accessTokenNode == null || accessTokenNode.isNull()) {
            throw new IOException("No access_token in IMS response: " + responseBody);
        }
        String accessToken = accessTokenNode.asText();

        LOG.info("Successfully obtained access token from IMS");
        JsonNode expiresInNode = json.get("expires_in");
        long expiry = expiresInNode != null && !expiresInNode.isNull()
                ? expiresInNode.asLong() * 1000 : 86400000L;
        tokenExpirationTime = now + expiry;
        cachedAccessToken = accessToken;

        return cachedAccessToken;
    }

    // ========== Collection Operations ==========

    private Collection doCreateCollection(String token, String name, String description) throws IOException {
        URL url = new URL(config.getYukonBaseUrl() + "/api/v2/collection");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("description", description != null ? description : "");
        payload.put("is_public", true);
        payload.put("is_frozen", false);
        payload.putObject("metadata");

        try (OutputStream out = conn.getOutputStream()) {
            objectMapper.writeValue(out, payload);
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();

        if (status < 200 || status >= 300) {
            LOG.error("Failed to create collection '{}', HTTP {}: {}", name, status, body);
            throw new IOException("Failed to create collection: HTTP " + status + " - " + body);
        }

        LOG.info("Created Yukon collection '{}' successfully", name);
        JsonNode json = objectMapper.readTree(body);
        String collectionId = json.has("namespace_id") ? json.get("namespace_id").asText() : null;
        return new Collection(collectionId, name, description);
    }

    // ========== Document Upload ==========

    private UploadResult doUploadDocument(String token, String collectionId, String fileName,
                                          String jsonContent) throws IOException {
        String boundary = "----DocStoreBoundary" + UUID.randomUUID();
        URL url = new URL(config.getYukonBaseUrl() + "/api/v2/collection/" + collectionId + "/upload");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try (OutputStream out = conn.getOutputStream()) {
            StringBuilder sb = new StringBuilder();
            sb.append(twoHyphens).append(boundary).append(lineEnd);
            sb.append("Content-Disposition: form-data; name=\"documents\"; filename=\"")
                    .append(fileName).append("\"").append(lineEnd);
            sb.append("Content-Type: application/json").append(lineEnd);
            sb.append(lineEnd);

            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            out.write(lineEnd.getBytes(StandardCharsets.UTF_8));

            String end = twoHyphens + boundary + twoHyphens + lineEnd;
            out.write(end.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();

        if (status >= 200 && status < 300) {
            LOG.debug("Uploaded {} to collection {} successfully", fileName, collectionId);
            JsonNode json = objectMapper.readTree(body);
            String documentId = json.has("document_id") ? json.get("document_id").asText() : fileName;
            return UploadResult.success(documentId, fileName);
        } else {
            LOG.error("Upload failed for {} (collection {}), HTTP {}: {}", fileName, collectionId, status, body);
            return UploadResult.failure(fileName, "HTTP " + status + ": " + body);
        }
    }

    // ========== Inference ==========

    private InferenceResult doAskQuestion(String token, String collectionId, String question,
                                          List<String> documentIds) throws IOException {
        URL url = new URL(config.getYukonBaseUrl() + "/api/v1/inference/question-answer/stream");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setRequestProperty("Content-Type", "application/json");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("request_id", UUID.randomUUID().toString());
        ArrayNode collectionsArray = payload.putArray("collections");
        collectionsArray.add(collectionId);
        payload.put("inputs", question);

        ObjectNode responseFormat = payload.putObject("response_format");
        responseFormat.put("format", "AUTO");
        responseFormat.put("style", "AUTO");
        responseFormat.put("tone", "AUTO");

        payload.putArray("document_tags");
        ArrayNode sourceOptions = payload.putArray("source_options");
        sourceOptions.add("COLLECTION");
        payload.put("inference_mode", "STANDARD");
        payload.put("inference_start_time", java.time.Instant.now().toString());

        if (documentIds != null && !documentIds.isEmpty()) {
            ArrayNode docIdsArray = payload.putArray("document_ids");
            for (String docId : documentIds) {
                docIdsArray.add(docId.trim());
            }
        }

        try (OutputStream out = conn.getOutputStream()) {
            objectMapper.writeValue(out, payload);
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            InputStream errorStream = conn.getErrorStream();
            String errorBody = readAll(errorStream);
            conn.disconnect();
            LOG.error("Yukon inference failed, HTTP {}: {}", status, errorBody);
            return InferenceResult.failure(question, collectionId, "HTTP " + status + ": " + errorBody);
        }

        StringBuilder fullAnswer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean streamComplete = false;
            while ((line = reader.readLine()) != null && !streamComplete) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6);
                    JsonNode eventJson = objectMapper.readTree(jsonData);

                    // Handle both array and object responses
                    if (eventJson.isArray()) {
                        for (JsonNode item : eventJson) {
                            if (item.has("generated_text")) {
                                String generatedText = item.get("generated_text").asText();
                                generatedText = generatedText.replaceAll("\\[\\^?\\d+]", "");
                                fullAnswer.append(generatedText);
                            }
                            if (item.has("stream_complete") && item.get("stream_complete").asBoolean()) {
                                streamComplete = true;
                                break;
                            }
                        }
                    } else {
                        if (eventJson.has("generated_text")) {
                            String generatedText = eventJson.get("generated_text").asText();
                            generatedText = generatedText.replaceAll("\\[\\^?\\d+]", "");
                            fullAnswer.append(generatedText);
                        }
                        if (eventJson.has("stream_complete") && eventJson.get("stream_complete").asBoolean()) {
                            streamComplete = true;
                        }
                    }
                }
            }
        }
        conn.disconnect();

        LOG.info("Inference completed for question: {}", question);
        return InferenceResult.success(question, collectionId, fullAnswer.toString());
    }

    // ========== Document Search ==========

    private SearchResult doSearchDocuments(String token, String collectionId, String query, int maxResults)
            throws IOException {
        // Use the same inference endpoint as askQuestion, but extract source documents
        URL url = new URL(config.getYukonBaseUrl() + "/api/v1/inference/question-answer/stream");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setRequestProperty("Content-Type", "application/json");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("request_id", UUID.randomUUID().toString());
        ArrayNode collectionsArray = payload.putArray("collections");
        collectionsArray.add(collectionId);
        payload.put("inputs", query);

        ObjectNode responseFormat = payload.putObject("response_format");
        responseFormat.put("format", "AUTO");
        responseFormat.put("style", "AUTO");
        responseFormat.put("tone", "AUTO");

        payload.putArray("document_tags");
        ArrayNode sourceOptions = payload.putArray("source_options");
        sourceOptions.add("COLLECTION");
        payload.put("inference_mode", "STANDARD");
        payload.put("inference_start_time", java.time.Instant.now().toString());

        try (OutputStream out = conn.getOutputStream()) {
            objectMapper.writeValue(out, payload);
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            InputStream errorStream = conn.getErrorStream();
            String errorBody = readAll(errorStream);
            conn.disconnect();
            LOG.error("Yukon search failed, HTTP {}: {}", status, errorBody);
            return SearchResult.failure(query, collectionId, "HTTP " + status + ": " + errorBody);
        }

        // Collect source documents from the streaming response
        List<SearchResult.DocumentInfo> documents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean streamComplete = false;
            while ((line = reader.readLine()) != null && !streamComplete) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6);
                    JsonNode eventJson = objectMapper.readTree(jsonData);

                    // Handle both array and object responses
                    JsonNode responseNode = eventJson.isArray() && eventJson.size() > 0
                            ? eventJson.get(0) : eventJson;

                    // Extract source documents
                    JsonNode sourceNode = responseNode.get("source");
                    if (sourceNode != null && sourceNode.isObject()) {
                        sourceNode.fields().forEachRemaining(entry -> {
                            JsonNode docInfo = entry.getValue();
                            String docId = docInfo.has("document_id")
                                    ? docInfo.get("document_id").asText() : null;
                            String docName = docInfo.has("document_name")
                                    ? docInfo.get("document_name").asText() : null;
                            String jcrPath = extractJcrPathFromFileName(docName);
                            if (docId != null && documents.size() < maxResults) {
                                // Avoid duplicates
                                boolean exists = documents.stream()
                                        .anyMatch(d -> d.getDocumentId().equals(docId));
                                if (!exists) {
                                    documents.add(new SearchResult.DocumentInfo(docId, jcrPath));
                                }
                            }
                        });
                    }

                    // Check for stream completion
                    if (responseNode.has("stream_complete")
                            && responseNode.get("stream_complete").asBoolean()) {
                        streamComplete = true;
                    }
                }
            }
        }
        conn.disconnect();

        LOG.info("Search completed for query '{}', found {} documents", query, documents.size());
        return SearchResult.success(query, collectionId, documents);
    }

    // ========== List Documents ==========

    private ListDocumentsResult doListDocuments(String token, String collectionId) throws IOException {
        List<ListDocumentsResult.DocumentInfo> documents = new ArrayList<>();
        int page = 1;
        int pageSize = 100; // Maximum allowed by Yukon API
        int totalPages;

        do {
            URL url = new URL(config.getYukonBaseUrl() + "/api/v1/collection/" + collectionId
                    + "/page?page=" + page + "&page_size=" + pageSize);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(is);
            conn.disconnect();

            if (status < 200 || status >= 300) {
                LOG.error("Yukon list documents failed, HTTP {}: {}", status, body);
                return ListDocumentsResult.failure(collectionId, "HTTP " + status + ": " + body);
            }

            JsonNode json = objectMapper.readTree(body);

            // Calculate total pages from response
            int total = json.has("total") ? json.get("total").asInt() : 0;
            totalPages = (total + pageSize - 1) / pageSize;

            // Response is an InfoPage object with a "pages" array containing DocumentInfo objects
            JsonNode pagesArray = json.get("pages");
            if (pagesArray != null && pagesArray.isArray()) {
                for (JsonNode item : pagesArray) {
                    String docId = item.has("document_id") ? item.get("document_id").asText() : null;
                    String docName = item.has("document_name") ? item.get("document_name").asText() : null;
                    String jcrPath = extractJcrPathFromFileName(docName);
                    if (docId != null) {
                        documents.add(new ListDocumentsResult.DocumentInfo(docId, jcrPath));
                    }
                }
            }

            page++;
        } while (page <= totalPages);

        LOG.info("Listed {} documents in collection '{}'", documents.size(), collectionId);
        return ListDocumentsResult.success(collectionId, documents);
    }

    // ========== Utility Methods ==========

    /**
     * Extracts the JCR path from a document filename.
     * The filename format is: path_with_underscores__variation.json
     * For example: content_dam_my-folder_my-cf__master.json -> /content/dam/my-folder/my-cf
     */
    private String extractJcrPathFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // Remove .json extension
        String name = fileName;
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }

        // Remove variation suffix (everything after __)
        int variationIndex = name.lastIndexOf("__");
        if (variationIndex > 0) {
            name = name.substring(0, variationIndex);
        }

        // Convert underscores back to slashes and add leading slash
        return "/" + name.replace("_", "/");
    }

    private String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
