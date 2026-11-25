package com.renewiq.dto;

/** reply = text for the chat bubble; action = a change the user still has to confirm (or null). */
public record AssistantResponse(String reply, PendingAction action,
        java.util.List<com.renewiq.service.KnowledgeService.Passage> sources,
        java.util.List<AssistantChart> charts) {

    public record PendingAction(Long subscriptionId, String subscriptionName, String status) { }
}
