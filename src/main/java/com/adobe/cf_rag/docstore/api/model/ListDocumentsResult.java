package com.adobe.cf_rag.docstore.api.model;

import java.util.List;

/**
 * Result of listing documents in a collection.
 */
public class ListDocumentsResult {

    private final String collectionId;
    private final List<DocumentInfo> documents;
    private final boolean success;
    private final String errorMessage;

    private ListDocumentsResult(String collectionId, List<DocumentInfo> documents,
                                boolean success, String errorMessage) {
        this.collectionId = collectionId;
        this.documents = documents;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ListDocumentsResult success(String collectionId, List<DocumentInfo> documents) {
        return new ListDocumentsResult(collectionId, documents, true, null);
    }

    public static ListDocumentsResult failure(String collectionId, String errorMessage) {
        return new ListDocumentsResult(collectionId, null, false, errorMessage);
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

    /**
     * Information about a document in the collection.
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

