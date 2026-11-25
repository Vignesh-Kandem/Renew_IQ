package com.renewiq.repository;

import com.renewiq.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    void deleteByConversationId(Long conversationId);
    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);
    List<ChatMessage> findTop20ByConversationIdOrderByIdDesc(Long conversationId);
    Optional<ChatMessage> findByConversationIdAndRequestId(Long conversationId, String requestId);
    Optional<ChatMessage> findByIdAndConversationId(Long id, Long conversationId);
}
