# CF-RAG API Specification

## Purpose

This specification defines the HTTP API for Content Fragment RAG (Retrieval Augmented Generation) operations. The CF-RAG API enables AEM Content Fragments to be exported to Adobe's Yukon document store and provides AI-powered question-answering and semantic search capabilities.

The API is exposed via a Sling servlet at `/bin/cf-export` and provides endpoints for:
- Creating collections in the document store
- Uploading Content Fragments to collections
- Asking AI-powered questions about collection content
- Searching for relevant documents using semantic search
- Listing all documents in a collection

---

## Requirements

### Requirement: Collection Creation

The system SHALL allow users to create new collections in the document store.

#### Scenario: Successful collection creation
- **WHEN** a GET request is made to `/bin/cf-export?action=createCollection&name={name}&description={description}`
- **THEN** a new collection is created in Yukon
- **AND** the response contains `collection_id` and `name` fields

#### Scenario: Missing collection name
- **WHEN** a GET request is made to `/bin/cf-export?action=createCollection` without a `name` parameter
- **THEN** the system returns HTTP 400 Bad Request

---

### Requirement: Content Fragment Upload

The system SHALL allow users to upload Content Fragments to a collection.

#### Scenario: Successful upload of Content Fragments
- **WHEN** a GET request is made to `/bin/cf-export?collectionId={id}&rootPath={path}&variation={variation}`
- **THEN** all Content Fragments under the root path are found
- **AND** each fragment is serialized to JSON with title, name, variation, and elements
- **AND** each JSON document is uploaded to the specified collection
- **AND** the response contains `success` and `failed` counts

#### Scenario: Upload all variations
- **WHEN** the `variation` parameter is set to `all`
- **THEN** the master variation and all named variations are uploaded for each Content Fragment

#### Scenario: Missing collection ID
- **WHEN** a GET request is made without a `collectionId` parameter
- **THEN** the system returns HTTP 400 Bad Request

---

### Requirement: AI Question Answering

The system SHALL allow users to ask questions about documents in a collection using AI inference.

#### Scenario: Successful question answering
- **WHEN** a GET request is made to `/bin/cf-export?action=askQuestion&collectionId={id}&question={question}`
- **THEN** the question is sent to Yukon's inference API
- **AND** the response contains `question`, `collectionId`, and `answer` fields

#### Scenario: Question with specific documents
- **WHEN** the `documentIds` parameter is provided as a comma-separated list
- **THEN** the inference is limited to the specified documents

#### Scenario: Missing required parameters
- **WHEN** `collectionId` or `question` is missing
- **THEN** the system returns HTTP 400 Bad Request

---

### Requirement: Semantic Document Search

The system SHALL allow users to search for documents relevant to a query using semantic search.

#### Scenario: Successful semantic search
- **WHEN** a GET request is made to `/bin/cf-export?action=searchDocuments&collectionId={id}&query={query}&maxResults={n}`
- **THEN** the query is sent to Yukon's inference API for semantic retrieval
- **AND** the response contains `query`, `collectionId`, `success`, and `documents` array
- **AND** each document in the array has `documentId` and `documentPath` (JCR path)

#### Scenario: Default max results
- **WHEN** the `maxResults` parameter is not provided
- **THEN** the system defaults to returning up to 10 documents

#### Scenario: Missing required parameters
- **WHEN** `collectionId` or `query` is missing
- **THEN** the system returns HTTP 400 Bad Request

---

### Requirement: Document Listing

The system SHALL allow users to list all documents in a collection.

#### Scenario: Successful document listing
- **WHEN** a GET request is made to `/bin/cf-export?action=listDocuments&collectionId={id}`
- **THEN** all documents in the collection are retrieved with pagination
- **AND** the response contains `collectionId`, `success`, and `documents` array
- **AND** each document has `documentId` and `documentPath` (JCR path extracted from filename)

#### Scenario: Pagination handling
- **WHEN** the collection contains more than 100 documents
- **THEN** the system automatically paginates through all pages to retrieve all documents

#### Scenario: Missing collection ID
- **WHEN** `collectionId` is missing
- **THEN** the system returns HTTP 400 Bad Request

---

### Requirement: JCR Path Extraction

The system SHALL convert Yukon document filenames back to JCR paths.

#### Scenario: Path extraction from filename
- **WHEN** a document filename is `content_dam_my-folder_my-cf__master.json`
- **THEN** the extracted JCR path is `/content/dam/my-folder/my-cf`

#### Scenario: Variation suffix removal
- **WHEN** a document filename contains a variation suffix (e.g., `__master`, `__web`)
- **THEN** the variation suffix is removed before path extraction

---

### Requirement: Authentication

The system SHALL authenticate with Yukon using Adobe IMS tokens.

#### Scenario: Token exchange
- **WHEN** an API call is made to Yukon
- **THEN** the system exchanges the authorization code for an access token via IMS
- **AND** the access token is cached until near expiration

#### Scenario: Token refresh
- **WHEN** the cached token is within 5 minutes of expiration
- **THEN** a new token is obtained before making the API call

