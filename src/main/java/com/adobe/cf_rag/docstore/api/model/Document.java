package com.adobe.cf_rag.docstore.api.model;

/**
 * Represents a JSON document in a document store collection.
 */
public class Document {

    private final String id;
    private final String fileName;
    private final String jsonContent;

    public Document(String id, String fileName, String jsonContent) {
        this.id = id;
        this.fileName = fileName;
        this.jsonContent = jsonContent;
    }

    /**
     * Returns the unique identifier of the document.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the file name of the document.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the JSON content of the document.
     */
    public String getJsonContent() {
        return jsonContent;
    }

    @Override
    public String toString() {
        return "Document{id='" + id + "', fileName='" + fileName + "'}";
    }
}

