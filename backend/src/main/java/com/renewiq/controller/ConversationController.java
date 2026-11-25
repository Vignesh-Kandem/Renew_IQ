package com.renewiq.controller;

import com.renewiq.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j @RestController @RequestMapping("/api/assistant/conversations")
public class ConversationController {
    private final ConversationStore store;
    private final AssistantService assistant;
    public ConversationController(ConversationStore store,AssistantService assistant){this.store=store;this.assistant=assistant;}
    public record Send(@NotBlank @Size(max=2000) String content,@NotNull @Size(max=100) List<@NotNull @Positive Long> documentIds,@NotNull UUID requestId) { }
    public record Rename(@NotBlank @Size(max=80) String title) { }
    public record Action(@NotNull Boolean confirm) { }
    @GetMapping public List<ConversationStore.Entry> list(){return store.list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ConversationStore.Entry create(){return store.create();}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){store.delete(id);}
    @PatchMapping("/{id}") public ConversationStore.Entry rename(@PathVariable long id,@Valid @RequestBody Rename in){return store.rename(id,in.title());}
    @GetMapping("/{id}") public ConversationStore.View view(@PathVariable long id){return store.view(id);}
    @PostMapping("/{id}/messages") public ConversationStore.View send(@PathVariable long id,@Valid @RequestBody Send in){
        var turn=store.begin(id,in.content().trim(),in.documentIds(),in.requestId());
        if (!turn.duplicate()) {
            com.renewiq.dto.AssistantResponse reply=null;
            String error=null;
            try { reply=assistant.chat(turn.history(),in.documentIds()); }
            catch(ResponseStatusException e){ error=e.getReason()==null?"The assistant could not answer. Please try again.":e.getReason(); }
            catch(RuntimeException e){log.warn("Assistant reply interrupted: {}",e.getClass().getSimpleName());error="The assistant could not finish this reply. Your message was saved; please try again.";}
            store.complete(id,reply,error);
        }
        return store.view(id);
    }
    @PostMapping("/{id}/messages/{messageId}/action")
    public ConversationStore.View action(@PathVariable long id,@PathVariable long messageId,@Valid @RequestBody Action in){return store.action(id,messageId,in.confirm());}
}
