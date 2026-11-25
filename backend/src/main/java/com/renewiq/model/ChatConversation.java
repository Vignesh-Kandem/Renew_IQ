package com.renewiq.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "chat_conversations")
@Getter @Setter @NoArgsConstructor
public class ChatConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_conversations_user"))
    private AppUser user;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    private LocalDateTime processingStartedAt;
}
