package com.renewiq.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "chat_messages")
@Getter @Setter @NoArgsConstructor
public class ChatMessage {
    public enum Role { USER, ASSISTANT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_messages_conversation"))
    private ChatConversation conversation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String metadata;
    @Column(length = 36)
    private String requestId;
}
