# Project Context

## Purpose
CF-RAG (Content Fragment RAG) is an AEM (Adobe Experience Manager) OSGi bundle that enables Retrieval Augmented Generation (RAG) capabilities for Content Fragments. It exports Content Fragments to Adobe's Yukon document store and provides AI-powered question-answering and semantic search functionality.

## Tech Stack
- Java 11+
- Apache Sling / AEM as a Cloud Service
- OSGi Declarative Services
- Adobe Yukon RAG API
- Adobe IMS for authentication
- Jackson for JSON processing
- Maven for build management

## Project Conventions

### Code Style
- Standard Java naming conventions (camelCase for methods/variables, PascalCase for classes)
- Package structure: `com.adobe.cf_rag`
  - `docstore.api` - Service interfaces and model classes
  - `docstore.yukon` - Yukon-specific implementation
  - `servlets` - Sling servlets exposing HTTP endpoints
- Use SLF4J for logging
- Use Jackson ObjectMapper for JSON serialization/deserialization

### Architecture Patterns
- **Service Abstraction**: `DocumentStoreService` interface abstracts document store operations, allowing different backend implementations
- **Result Pattern**: Operations return result objects (e.g., `SearchResult`, `InferenceResult`) with success/failure status and error messages
- **OSGi Configuration**: Servlet configuration via `@ObjectClassDefinition` annotations
- **Streaming Response Handling**: Yukon inference API uses Server-Sent Events (SSE) for streaming responses

### Testing Strategy
- Unit tests for service implementations
- Integration tests require AEM instance and Yukon API access
- Manual testing via curl commands to servlet endpoints

### Git Workflow
- Feature branches for new development
- Pull requests for code review before merging

## Domain Context
- **Content Fragments**: AEM's structured content type, stored in DAM (Digital Asset Manager)
- **Variations**: Content Fragments can have multiple variations (master + named variations)
- **Yukon**: Adobe's internal RAG platform providing document storage, embedding, and AI inference
- **Collections**: Yukon organizes documents into collections (namespaces)
- **JCR Path**: Content Fragment paths in AEM's Java Content Repository (e.g., `/content/dam/my-folder/my-cf`)

## Important Constraints
- Yukon API pagination limit: 100 documents per page
- IMS token exchange required for Yukon API authentication
- Content Fragment paths are encoded in document filenames (slashes become underscores)
- Streaming inference responses require SSE parsing

## External Dependencies
- **Adobe IMS**: Token exchange for API authentication (`ims-na1.adobelogin.com`)
- **Adobe Yukon API**: RAG platform (`https://yukon.adobe.io`)
  - `/api/v1/collection` - Collection management
  - `/api/v1/collection/{id}/page` - Document listing with pagination
  - `/api/v1/inference/question-answer/stream` - AI inference (Q&A and semantic search)
  - `/api/v2/collection/{id}/document` - Document upload
- **AEM QueryBuilder**: For finding Content Fragments in the repository
