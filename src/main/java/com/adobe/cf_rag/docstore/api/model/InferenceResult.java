package com.adobe.cf_rag.docstore.api.model;

/**
 * Represents the result of an inference query (question-answering) against a document store.
 */
public class InferenceResult {

    private final String question;
    private final String collectionId;
    private final String answer;
    private final boolean success;
    private final String errorMessage;

    private InferenceResult(String question, String collectionId, String answer,
                            boolean success, String errorMessage) {
        this.question = question;
        this.collectionId = collectionId;
        this.answer = answer;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful inference result.
     */
    public static InferenceResult success(String question, String collectionId, String answer) {
        return new InferenceResult(question, collectionId, answer, true, null);
    }

    /**
     * Creates a failed inference result.
     */
    public static InferenceResult failure(String question, String collectionId, String errorMessage) {
        return new InferenceResult(question, collectionId, null, false, errorMessage);
    }

    public String getQuestion() {
        return question;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public String getAnswer() {
        return answer;
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
            return "InferenceResult{question='" + question + "', answer='" + answer + "'}";
        } else {
            return "InferenceResult{question='" + question + "', error='" + errorMessage + "'}";
        }
    }
}

