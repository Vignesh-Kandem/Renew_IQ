package com.renewiq.repository;

import com.renewiq.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    java.util.List<KnowledgeDocument> findByUserIdOrderByUploadedAtDesc(Long userId);
    java.util.Optional<KnowledgeDocument> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    java.util.List<KnowledgeDocument> findByUserIdAndIdInOrderByUploadedAtDesc(Long userId, java.util.List<Long> ids);
}
