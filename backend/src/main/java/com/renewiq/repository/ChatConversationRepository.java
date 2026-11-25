package com.renewiq.repository;

import com.renewiq.model.ChatConversation;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    List<ChatConversation> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id and c.user.id = :userId")
    Optional<ChatConversation> lockOwned(Long id, Long userId);
}
