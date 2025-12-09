package com.adobe.cf_rag.docstore.api;

import com.adobe.cf_rag.docstore.api.model.Collection;
import com.adobe.cf_rag.docstore.api.model.InferenceResult;
import com.adobe.cf_rag.docstore.api.model.ListDocumentsResult;
import com.adobe.cf_rag.docstore.api.model.SearchResult;
import com.adobe.cf_rag.docstore.api.model.UploadResult;

import java.util.List;

/**
 * Service interface for interacting with a document store.
 * Provides operations for managing collections, uploading JSON documents,
 * and performing inference queries.
 */
public interface DocumentStoreService {

    /**
     * Creates a new collection in the document store.
     *
     * @param name        the name of the collection
     * @param description optional description of the collection
     * @return the created Collection with its assigned ID
     * @throws DocumentStoreException if the collection cannot be created
     */
    Collection createCollection(String name, String description) throws DocumentStoreException;

    /**
     * Uploads a JSON document to a collection.
     *
     * @param collectionId the ID of the target collection
     * @param fileName     the name of the document file
     * @param jsonContent  the JSON content of the document
     * @return the result of the upload operation
     * @throws DocumentStoreException if the upload fails
     */
    UploadResult uploadDocument(String collectionId, String fileName, String jsonContent)
            throws DocumentStoreException;

    /**
     * Asks a question about documents in a collection using AI inference.
     *
     * @param collectionId the ID of the collection to query
     * @param question     the question to ask
     * @param documentIds  optional list of specific document IDs to query (null for all)
     * @return the inference result containing the answer
     * @throws DocumentStoreException if the inference fails
     */
    InferenceResult askQuestion(String collectionId, String question, List<String> documentIds)
            throws DocumentStoreException;

    /**
     * Searches for documents in a collection that are relevant to the given query.
     *
     * @param collectionId the ID of the collection to search
     * @param query        the search query
     * @param maxResults   maximum number of document IDs to return
     * @return the search result containing a list of matching document IDs
     * @throws DocumentStoreException if the search fails
     */
    SearchResult searchDocuments(String collectionId, String query, int maxResults)
            throws DocumentStoreException;

    /**
     * Lists all documents in a collection.
     *
     * @param collectionId the ID of the collection
     * @return the result containing a list of documents with their IDs and names
     * @throws DocumentStoreException if the listing fails
     */
    ListDocumentsResult listDocuments(String collectionId) throws DocumentStoreException;

    /**
     * Returns the name of this document store implementation.
     * For example: "Yukon", "Elasticsearch", etc.
     */
    String getProviderName();
}

