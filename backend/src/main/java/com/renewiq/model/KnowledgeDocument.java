package com.renewiq.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "knowledge_documents")
@Getter @Setter @NoArgsConstructor
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_documents_user"))
    private AppUser user;
    @Column(nullable = false, length = 180)
    private String name;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;
    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();
}
