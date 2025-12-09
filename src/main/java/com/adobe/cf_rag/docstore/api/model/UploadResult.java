package com.adobe.cf_rag.docstore.api.model;

/**
 * Represents the result of uploading a document to a collection.
 */
public class UploadResult {

    private final String documentId;
    private final String fileName;
    private final boolean success;
    private final String errorMessage;

    private UploadResult(String documentId, String fileName, boolean success, String errorMessage) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful upload result.
     */
    public static UploadResult success(String documentId, String fileName) {
        return new UploadResult(documentId, fileName, true, null);
    }

    /**
     * Creates a failed upload result.
     */
    public static UploadResult failure(String fileName, String errorMessage) {
        return new UploadResult(null, fileName, false, errorMessage);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFileName() {
        return fileName;
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
            return "UploadResult{documentId='" + documentId + "', fileName='" + fileName + "'}";
        } else {
            return "UploadResult{fileName='" + fileName + "', error='" + errorMessage + "'}";
        }
    }
}

