package com.adobe.cf_rag.servlets;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentVariation;
import com.adobe.cq.dam.cfm.VariationDef;
import com.adobe.cq.dam.cfm.FragmentData;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.adobe.cf_rag.docstore.api.DocumentStoreException;
import com.adobe.cf_rag.docstore.api.DocumentStoreService;
import com.adobe.cf_rag.docstore.api.model.Collection;
import com.adobe.cf_rag.docstore.api.model.InferenceResult;
import com.adobe.cf_rag.docstore.api.model.ListDocumentsResult;
import com.adobe.cf_rag.docstore.api.model.UploadResult;
import com.adobe.cf_rag.docstore.yukon.YukonConfig;
import com.adobe.cf_rag.docstore.yukon.YukonDocumentStoreService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * Sling servlet that exports Content Fragments to a document store.
 * Uses the DocumentStoreService abstraction layer.
 *
 * Endpoint:
 *   GET /bin/cf-export?rootPath=...&collectionId=...&variation=...
 *
 * Actions:
 *   - (default): Upload content fragments to collection
 *   - action=createCollection&name=...&description=...: Create a new collection
 *   - action=askQuestion&collectionId=...&question=...: Ask a question about the collection
 *   - action=searchDocuments&collectionId=...&query=...&maxResults=...: Search for relevant documents
 *   - action=listDocuments&collectionId=...: List all documents in a collection
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.paths=/bin/cf-export"
        }
)
@Designate(ocd = CfRagServlet.Config.class)
public class CfRagServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(CfRagServlet.class);

    @ObjectClassDefinition(
            name = "CF RAG Servlet Configuration",
            description = "Configuration for document store integration"
    )
    public @interface Config {
        @AttributeDefinition(name = "Client ID", description = "IMS service account client ID (API Key)")
        String clientId() default "";

        @AttributeDefinition(name = "Client Secret", description = "IMS service account client secret")
        String clientSecret() default "";

        @AttributeDefinition(name = "Authorization Code", description = "IMS permanent authorization code")
        String authorizationCode() default "";

        @AttributeDefinition(name = "IMS Host", description = "IMS host for token exchange")
        String imsHost() default "ims-na1.adobelogin.com";

        @AttributeDefinition(name = "Yukon Base URL", description = "Base URL for Yukon API")
        String yukonBaseUrl() default "https://yukon.adobe.io";
    }

    private DocumentStoreService documentStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private QueryBuilder queryBuilder;

    @Activate
    @Modified
    protected void activate(Config config) {
        YukonConfig yukonConfig = YukonConfig.builder()
                .clientId(config.clientId())
                .clientSecret(config.clientSecret())
                .authorizationCode(config.authorizationCode())
                .imsHost(config.imsHost())
                .yukonBaseUrl(config.yukonBaseUrl())
                .build();
        this.documentStore = new YukonDocumentStoreService(yukonConfig);
        LOG.info("CfRagServlet configured with {} provider", documentStore.getProviderName());
    }

    @Override
    protected void doGet(@Nonnull SlingHttpServletRequest request, @Nonnull SlingHttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        try {
            if ("createCollection".equals(action)) {
                handleCreateCollection(request, response);
            } else if ("askQuestion".equals(action)) {
                handleAskQuestion(request, response);
            } else if ("searchDocuments".equals(action)) {
                handleSearchDocuments(request, response);
            } else if ("listDocuments".equals(action)) {
                handleListDocuments(request, response);
            } else {
                handleUpload(request, response);
            }
        } catch (DocumentStoreException e) {
            LOG.error("Document store error", e);
            int statusCode = e.getStatusCode() > 0 ? e.getStatusCode() : 500;
            response.sendError(statusCode, e.getMessage());
        }
    }

    private void handleCreateCollection(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException, DocumentStoreException {
        String name = request.getParameter("name");
        String description = request.getParameter("description");

        if (name == null || name.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Collection name is required");
            return;
        }

        Collection collection = documentStore.createCollection(name, description);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("collection_id", collection.getId());
        result.put("name", collection.getName());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }

    private void handleAskQuestion(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException, DocumentStoreException {
        String collectionId = request.getParameter("collectionId");
        String question = request.getParameter("question");
        String documentIdsParam = request.getParameter("documentIds");

        if (collectionId == null || collectionId.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "collectionId is required");
            return;
        }
        if (question == null || question.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "question is required");
            return;
        }

        List<String> documentIds = documentIdsParam != null && !documentIdsParam.isEmpty()
                ? Arrays.asList(documentIdsParam.split(","))
                : null;

        InferenceResult inferenceResult = documentStore.askQuestion(collectionId, question, documentIds);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("question", inferenceResult.getQuestion());
        result.put("collectionId", inferenceResult.getCollectionId());
        result.put("answer", inferenceResult.getAnswer());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }

    private void handleSearchDocuments(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException, DocumentStoreException {
        String collectionId = request.getParameter("collectionId");
        String query = request.getParameter("query");
        String maxResultsParam = request.getParameter("maxResults");

        if (collectionId == null || collectionId.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "collectionId is required");
            return;
        }
        if (query == null || query.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "query is required");
            return;
        }

        int maxResults = 10; // default
        if (maxResultsParam != null && !maxResultsParam.isEmpty()) {
            try {
                maxResults = Integer.parseInt(maxResultsParam);
            } catch (NumberFormatException e) {
                response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "maxResults must be a number");
                return;
            }
        }

        com.adobe.cf_rag.docstore.api.model.SearchResult searchResult =
                documentStore.searchDocuments(collectionId, query, maxResults);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", searchResult.getQuery());
        result.put("collectionId", searchResult.getCollectionId());
        result.put("success", searchResult.isSuccess());

        if (searchResult.isSuccess()) {
            ArrayNode documents = result.putArray("documents");
            for (com.adobe.cf_rag.docstore.api.model.SearchResult.DocumentInfo doc : searchResult.getDocuments()) {
                ObjectNode docNode = documents.addObject();
                docNode.put("documentId", doc.getDocumentId());
                if (doc.getDocumentPath() != null) {
                    docNode.put("documentPath", doc.getDocumentPath());
                }
            }
        } else {
            result.put("errorMessage", searchResult.getErrorMessage());
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }

    private void handleListDocuments(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException, DocumentStoreException {
        String collectionId = request.getParameter("collectionId");

        if (collectionId == null || collectionId.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "collectionId is required");
            return;
        }

        ListDocumentsResult listResult = documentStore.listDocuments(collectionId);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("collectionId", listResult.getCollectionId());
        result.put("success", listResult.isSuccess());

        if (listResult.isSuccess()) {
            ArrayNode documents = result.putArray("documents");
            for (ListDocumentsResult.DocumentInfo doc : listResult.getDocuments()) {
                ObjectNode docNode = documents.addObject();
                docNode.put("documentId", doc.getDocumentId());
                if (doc.getDocumentPath() != null) {
                    docNode.put("documentPath", doc.getDocumentPath());
                }
            }
        } else {
            result.put("errorMessage", listResult.getErrorMessage());
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }

    private void handleUpload(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException, DocumentStoreException {
        ResourceResolver resolver = request.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to adapt to JCR Session");
            return;
        }

        String rootPath = Optional.ofNullable(request.getParameter("rootPath"))
                .filter(s -> !s.isEmpty()).orElse("/content/dam");
        String variationParam = Optional.ofNullable(request.getParameter("variation"))
                .filter(s -> !s.isEmpty()).orElse("master");
        String collectionId = request.getParameter("collectionId");

        if (collectionId == null || collectionId.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "collectionId is required");
            return;
        }

        // Find content fragments
        Map<String, String> predicates = new HashMap<>();
        predicates.put("path", rootPath);
        predicates.put("type", "dam:Asset");
        predicates.put("1_property", "jcr:content/contentFragment");
        predicates.put("1_property.value", "true");
        predicates.put("p.limit", "-1");

        Query query = queryBuilder.createQuery(PredicateGroup.create(predicates), session);
        SearchResult searchResult = query.getResult();

        List<UploadResult> uploadResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Hit hit : searchResult.getHits()) {
            try {
                Resource cfResource = hit.getResource();
                ContentFragment cf = cfResource.adaptTo(ContentFragment.class);
                if (cf == null) continue;

                List<String> variationsToExport = getVariationsToExport(cf, variationParam);
                for (String variation : variationsToExport) {
                    String fileName = buildFileName(cfResource.getPath(), variation);
                    String jsonContent = buildJsonContent(cf, variation);

                    UploadResult uploadResult = documentStore.uploadDocument(collectionId, fileName, jsonContent);
                    uploadResults.add(uploadResult);

                    if (uploadResult.isSuccess()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }
            } catch (Exception e) {
                LOG.error("Error processing content fragment", e);
                failCount++;
            }
        }

        ObjectNode responseJson = objectMapper.createObjectNode();
        responseJson.put("success", successCount);
        responseJson.put("failed", failCount);
        responseJson.put("collectionId", collectionId);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), responseJson);
    }

    private List<String> getVariationsToExport(ContentFragment cf, String variationParam) {
        List<String> variations = new ArrayList<>();
        if ("all".equalsIgnoreCase(variationParam)) {
            variations.add("master");
            Iterator<VariationDef> it = cf.listAllVariations();
            while (it.hasNext()) {
                variations.add(it.next().getName());
            }
        } else {
            variations.add(variationParam);
        }
        return variations;
    }

    private String buildFileName(String path, String variation) {
        String safePath = path.replace("/", "_").replace(":", "_");
        if (safePath.startsWith("_")) safePath = safePath.substring(1);
        return safePath + "__" + variation + ".json";
    }

    private String buildJsonContent(ContentFragment cf, String variation) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", cf.getTitle());
        root.put("name", cf.getName());
        root.put("variation", variation);

        ObjectNode elements = root.putObject("elements");

        for (Iterator<ContentElement> it = cf.getElements(); it.hasNext(); ) {
            ContentElement elem = it.next();

            Object value;
            if ("master".equals(variation)) {
                FragmentData data = elem.getValue();
                value = data != null ? data.getValue() : null;
            } else {
                ContentVariation cv = elem.getVariation(variation);
                if (cv != null) {
                    FragmentData data = cv.getValue();
                    value = data != null ? data.getValue() : null;
                } else {
                    FragmentData data = elem.getValue();
                    value = data != null ? data.getValue() : null;
                }
            }

            addValueToNode(elements, elem.getName(), value);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            LOG.error("Error serializing content fragment to JSON", e);
            return "{}";
        }
    }

    private void addValueToNode(ObjectNode node, String fieldName, Object value) {
        if (value == null) {
            node.putNull(fieldName);
        } else if (value instanceof String) {
            node.put(fieldName, (String) value);
        } else if (value instanceof Integer) {
            node.put(fieldName, (Integer) value);
        } else if (value instanceof Long) {
            node.put(fieldName, (Long) value);
        } else if (value instanceof Double) {
            node.put(fieldName, (Double) value);
        } else if (value instanceof Float) {
            node.put(fieldName, (Float) value);
        } else if (value instanceof Boolean) {
            node.put(fieldName, (Boolean) value);
        } else if (value.getClass().isArray()) {
            ArrayNode arrayNode = node.putArray(fieldName);
            Object[] arr = (Object[]) value;
            for (Object item : arr) {
                if (item instanceof String) {
                    arrayNode.add((String) item);
                } else if (item instanceof Integer) {
                    arrayNode.add((Integer) item);
                } else if (item instanceof Long) {
                    arrayNode.add((Long) item);
                } else if (item instanceof Double) {
                    arrayNode.add((Double) item);
                } else if (item instanceof Boolean) {
                    arrayNode.add((Boolean) item);
                } else if (item != null) {
                    arrayNode.add(item.toString());
                }
            }
        } else {
            node.put(fieldName, value.toString());
        }
    }
}

