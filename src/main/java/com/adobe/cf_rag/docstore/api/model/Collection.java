package com.adobe.cf_rag.docstore.api.model;

/**
 * Represents a document collection in a document store.
 */
public class Collection {

    private final String id;
    private final String name;
    private final String description;

    public Collection(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /**
     * Returns the unique identifier of the collection.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable name of the collection.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description of the collection.
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Collection{id='" + id + "', name='" + name + "'}";
    }
}

