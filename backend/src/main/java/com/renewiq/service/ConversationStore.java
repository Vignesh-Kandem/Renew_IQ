package com.renewiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renewiq.dto.*;
import com.renewiq.model.*;
import com.renewiq.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Short transactions save each turn; no database lock is held during the provider call. */
@Service @Transactional(readOnly = true)
public class ConversationStore {
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final CurrentUser current;
    private final KnowledgeService knowledge;
    private final SubscriptionService subscriptions;
    private final ObjectMapper json;
    public ConversationStore(ChatConversationRepository conversations, ChatMessageRepository messages, CurrentUser current,
                             KnowledgeService knowledge, SubscriptionService subscriptions, ObjectMapper json) {
        this.conversations=conversations; this.messages=messages; this.current=current;
        this.knowledge=knowledge; this.subscriptions=subscriptions; this.json=json;
    }
    public record Entry(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record Metadata(boolean error, List<KnowledgeService.DocumentView> attachments,
            List<KnowledgeService.Passage> sources, List<AssistantChart> charts,
            AssistantResponse.PendingAction action, String actionState) { }
    public record MessageView(Long id, String role, String content, LocalDateTime createdAt, Metadata metadata) { }
    public record View(Long id, String title, List<MessageView> messages, List<Long> documentIds, boolean processing) { }
    public record Turn(boolean duplicate, List<AssistantRequest.Msg> history) { }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private boolean processing(ChatConversation c) {
        return c.getProcessingStartedAt()!=null && c.getProcessingStartedAt().isAfter(now().minusMinutes(10));
    }
    public List<Entry> list() {
        return conversations.findByUserIdOrderByUpdatedAtDescIdDesc(current.id()).stream()
                .map(c -> new Entry(c.getId(),c.getTitle(),c.getCreatedAt(),c.getUpdatedAt())).toList();
    }
    private ChatConversation owned(long id, boolean lock) {
        return (lock ? conversations.lockOwned(id,current.id()) : conversations.findByIdAndUserId(id,current.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Conversation not found."));
    }
    public View view(long id) {
        ChatConversation c=owned(id,false);
        var items=messages.findByConversationIdOrderByIdAsc(id).stream()
                .map(m -> new MessageView(m.getId(),m.getRole().name().toLowerCase(Locale.ROOT),m.getContent(),m.getCreatedAt(),decode(m))).toList();
        List<Long> ids=List.of();
        for (var m:items) if (m.role().equals("user")) ids=m.metadata().attachments()==null ? List.of() : m.metadata().attachments().stream().map(KnowledgeService.DocumentView::id).toList();
        return new View(c.getId(),c.getTitle(),items,ids,processing(c));
    }
    @Transactional public Entry create() {
        var c=new ChatConversation(); c.setTitle("New chat"); c.setUser(current.get()); conversations.save(c);
        return new Entry(c.getId(),c.getTitle(),c.getCreatedAt(),c.getUpdatedAt());
    }
    @Transactional public void delete(long id) {
        var c=owned(id,true);
        if(processing(c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wait for this chat to finish generating before deleting it.");
        messages.deleteByConversationId(id);
        messages.flush();
        conversations.delete(c);
    }
    @Transactional public Entry rename(long id, String title) {
        var c=owned(id,true);
        String normalized=title.strip().replaceAll("\\s+"," ");
        if(normalized.isBlank()||normalized.length()>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chat title must contain 1 to 80 characters.");
        c.setTitle(normalized);
        conversations.save(c);
        return new Entry(c.getId(),c.getTitle(),c.getCreatedAt(),c.getUpdatedAt());
    }
    @Transactional public Turn begin(long id, String content, List<Long> documentIds, UUID requestId) {
        var c=owned(id,true);
        if (processing(c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"This conversation is still generating a reply. Reopen it in a moment.");
        var previous=messages.findByConversationIdAndRequestId(id,requestId.toString());
        if (previous.isPresent()) {
            if(!previous.get().getContent().equals(content))throw new ResponseStatusException(HttpStatus.CONFLICT,"This request ID already belongs to a different message.");
            return new Turn(true,List.of());
        }
        if (c.getProcessingStartedAt()!=null) {
            add(c,ChatMessage.Role.ASSISTANT,"The previous reply was interrupted. You can continue this conversation.",
                new Metadata(true,List.of(),List.of(),List.of(),null,null),null);
        }
        Set<Long> selection=new HashSet<>(documentIds);
        var attachments=selection.isEmpty() ? List.<KnowledgeService.DocumentView>of() : knowledge.list().stream().filter(d -> selection.contains(d.id())).toList();
        boolean firstMessage=messages.findTop20ByConversationIdOrderByIdDesc(id).isEmpty();
        add(c,ChatMessage.Role.USER,content,new Metadata(false,attachments,List.of(),List.of(),null,null),requestId.toString());
        if (firstMessage && c.getTitle().equals("New chat")) {
            String title=content.replaceAll("\\s+"," ");
            c.setTitle(title.substring(0,title.offsetByCodePoints(0,Math.min(80,title.codePointCount(0,title.length())))));
        }
        c.setUpdatedAt(now()); c.setProcessingStartedAt(now()); conversations.save(c);
        var recent=new ArrayList<>(messages.findTop20ByConversationIdOrderByIdDesc(id)); Collections.reverse(recent);
        var history=new ArrayList<AssistantRequest.Msg>();
        for (var m:recent) if (!decode(m).error()) history.add(new AssistantRequest.Msg(m.getRole().name().toLowerCase(Locale.ROOT), m.getContent().substring(0,Math.min(2000,m.getContent().length()))));
        while (!history.isEmpty() && !history.get(0).role().equals("user")) history.remove(0);
        return new Turn(false,history);
    }
    @Transactional public void complete(long id, AssistantResponse reply, String error) {
        var c=owned(id,true);
        if (error!=null) add(c,ChatMessage.Role.ASSISTANT,error,new Metadata(true,List.of(),List.of(),List.of(),null,null),null);
        else add(c,ChatMessage.Role.ASSISTANT,reply.reply(),new Metadata(false,List.of(),reply.sources(),reply.charts(),reply.action(),reply.action()==null?null:"pending"),null);
        c.setUpdatedAt(now()); c.setProcessingStartedAt(null); conversations.save(c);
    }
    @Transactional public View action(long id,long messageId,boolean confirm) {
        owned(id,true);
        var m=messages.findByIdAndConversationId(messageId,id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Message not found."));
        var meta=decode(m);
        if (m.getRole()!=ChatMessage.Role.ASSISTANT || meta.action()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No action on this message.");
        if ("pending".equals(meta.actionState())) {
            if (confirm) subscriptions.setStatus(meta.action().subscriptionId(),SubscriptionStatus.valueOf(meta.action().status()));
            m.setMetadata(encode(new Metadata(meta.error(),meta.attachments(),meta.sources(),meta.charts(),meta.action(),confirm?"done":"dismissed")));
            messages.save(m);
        }
        return view(id);
    }
    private void add(ChatConversation c,ChatMessage.Role role,String content,Metadata meta,String requestId) {
        var m=new ChatMessage();m.setConversation(c);m.setRole(role);m.setContent(content);m.setMetadata(encode(meta));m.setRequestId(requestId);messages.saveAndFlush(m);
    }
    private String encode(Metadata metadata) {
        try{return json.writeValueAsString(metadata);}catch(JsonProcessingException e){throw new IllegalStateException("Could not store chat details",e);}
    }
    private Metadata decode(ChatMessage message) {
        if (message.getMetadata()==null) return new Metadata(false,List.of(),List.of(),List.of(),null,null);
        try{return json.readValue(message.getMetadata(),Metadata.class);}catch(JsonProcessingException e){throw new IllegalStateException("Could not read chat details",e);}
    }
}
