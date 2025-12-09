# CF-RAG API Design

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         AEM Instance                            │
│  ┌─────────────────┐    ┌──────────────────────────────────┐    │
│  │  CfRagServlet   │───▶│    DocumentStoreService          │    │
│  │  /bin/cf-export │    │    (interface)                   │    │
│  └─────────────────┘    └──────────────────────────────────┘    │
│                                      │                          │
│                                      ▼                          │
│                         ┌──────────────────────────────────┐    │
│                         │  YukonDocumentStoreService       │    │
│                         │  (implementation)                │    │
│                         └──────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────────┐
                    │           Adobe IMS                  │
                    │   (token exchange)                   │
                    └──────────────────────────────────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────────┐
                    │         Adobe Yukon API              │
                    │   - Collection management            │
                    │   - Document storage                 │
                    │   - AI inference                     │
                    └──────────────────────────────────────┘
```

## Key Components

### CfRagServlet
- **Location**: `servlets/CfRagServlet.java`
- **Purpose**: HTTP endpoint for all CF-RAG operations
- **Pattern**: Action-based routing via `action` query parameter
- **Configuration**: OSGi `@ObjectClassDefinition` for IMS/Yukon credentials

### DocumentStoreService Interface
- **Location**: `docstore/api/DocumentStoreService.java`
- **Purpose**: Abstraction layer for document store operations
- **Methods**:
  - `createCollection(name, description)` → `Collection`
  - `uploadDocument(collectionId, fileName, jsonContent)` → `UploadResult`
  - `askQuestion(collectionId, question, documentIds)` → `InferenceResult`
  - `searchDocuments(collectionId, query, maxResults)` → `SearchResult`
  - `listDocuments(collectionId)` → `ListDocumentsResult`

### YukonDocumentStoreService
- **Location**: `docstore/yukon/YukonDocumentStoreService.java`
- **Purpose**: Yukon-specific implementation of DocumentStoreService
- **Features**:
  - IMS token caching with automatic refresh
  - SSE streaming response parsing for inference
  - Pagination handling for document listing
  - JCR path extraction from document filenames

## Data Flow

### Content Fragment Upload
1. QueryBuilder finds all Content Fragments under root path
2. Each fragment is adapted to `ContentFragment` API
3. Fragment data is serialized to JSON (title, name, variation, elements)
4. Filename is generated: `{path}___{variation}.json` (slashes → underscores)
5. JSON is uploaded to Yukon via `/api/v2/collection/{id}/document`

### Semantic Search
1. Query is sent to Yukon inference API (`/api/v1/inference/question-answer/stream`)
2. SSE response is parsed for `source` field containing matched documents
3. Document IDs and names are extracted from source
4. Filenames are converted back to JCR paths

### Document Listing
1. Paginated requests to `/api/v1/collection/{id}/page?page={n}&page_size=100`
2. All pages are fetched until total is reached
3. Document names are converted to JCR paths

## Configuration

OSGi configuration properties:
- `clientId` - IMS API Key
- `clientSecret` - IMS client secret
- `authorizationCode` - IMS permanent authorization code
- `imsHost` - IMS endpoint (default: `ims-na1.adobelogin.com`)
- `yukonBaseUrl` - Yukon API base URL (default: `https://yukon.adobe.io`)

## Error Handling

- All operations return result objects with `success` boolean and `errorMessage`
- HTTP errors from Yukon are captured and returned in error messages
- `DocumentStoreException` wraps all failures with optional HTTP status code

## Filename Encoding

Content Fragment paths are encoded for Yukon storage:
- `/content/dam/my-folder/my-cf` → `content_dam_my-folder_my-cf__master.json`
- Encoding: Replace `/` with `_`, append `__{variation}.json`
- Decoding: Remove `.json`, remove `__{variation}`, replace `_` with `/`, add leading `/`

