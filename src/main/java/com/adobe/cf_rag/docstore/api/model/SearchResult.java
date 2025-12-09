package com.adobe.cf_rag.docstore.api.model;

import java.util.List;

/**
 * Represents the result of a document search query.
 */
public class SearchResult {

    private final String query;
    private final String collectionId;
    private final List<DocumentInfo> documents;
    private final boolean success;
    private final String errorMessage;

    private SearchResult(String query, String collectionId, List<DocumentInfo> documents,
                         boolean success, String errorMessage) {
        this.query = query;
        this.collectionId = collectionId;
        this.documents = documents;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful search result.
     */
    public static SearchResult success(String query, String collectionId, List<DocumentInfo> documents) {
        return new SearchResult(query, collectionId, documents, true, null);
    }

    /**
     * Creates a failed search result.
     */
    public static SearchResult failure(String query, String collectionId, String errorMessage) {
        return new SearchResult(query, collectionId, null, false, errorMessage);
    }

    public String getQuery() {
        return query;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public List<DocumentInfo> getDocuments() {
        return documents;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "SearchResult{query='" + query + "', documents=" + documents + "}";
        } else {
            return "SearchResult{query='" + query + "', error='" + errorMessage + "'}";
        }
    }

    /**
     * Information about a document in the search results.
     */
    public static class DocumentInfo {
        private final String documentId;
        private final String documentPath;

        public DocumentInfo(String documentId, String documentPath) {
            this.documentId = documentId;
            this.documentPath = documentPath;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getDocumentPath() {
            return documentPath;
        }

        @Override
        public String toString() {
            return "DocumentInfo{documentId='" + documentId + "', documentPath='" + documentPath + "'}";
        }
    }
}

