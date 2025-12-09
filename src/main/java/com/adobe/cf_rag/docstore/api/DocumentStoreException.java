package com.adobe.cf_rag.docstore.api;

/**
 * Exception thrown when a document store operation fails.
 */
public class DocumentStoreException extends Exception {

    private final int statusCode;

    public DocumentStoreException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public DocumentStoreException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public DocumentStoreException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DocumentStoreException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code if applicable, or -1 if not available.
     */
    public int getStatusCode() {
        return statusCode;
    }
}

