package com.renewiq.controller;

import com.renewiq.dto.AssistantRequest;
import com.renewiq.dto.AssistantResponse;
import com.renewiq.dto.ConfirmRequest;
import com.renewiq.model.Subscription;
import com.renewiq.service.AssistantService;
import com.renewiq.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistant;
    private final SubscriptionService subscriptions;

    @PostMapping("/chat")
    public AssistantResponse chat(@Valid @RequestBody AssistantRequest req) {
        return assistant.chat(req.messages(), req.documentIds());
    }

    /** Called when the user presses Confirm on a change the assistant proposed. */
    @PostMapping("/confirm")
    public Subscription confirm(@Valid @RequestBody ConfirmRequest req) {
        return subscriptions.setStatus(req.subscriptionId(), req.status());
    }
}
