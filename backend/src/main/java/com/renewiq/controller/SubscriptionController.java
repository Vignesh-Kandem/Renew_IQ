package com.renewiq.controller;

import com.renewiq.model.Subscription;
import com.renewiq.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    @GetMapping
    public List<Subscription> all() { return service.findAll(); }

    @GetMapping("/upcoming")
    public List<Subscription> upcoming(@RequestParam(defaultValue = "30") int days) {
        return service.upcoming(days);
    }

    @GetMapping("/{id}")
    public Subscription one(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Subscription create(@Valid @RequestBody Subscription s) { return service.create(s); }

    @PutMapping("/{id}")
    public Subscription update(@PathVariable Long id, @Valid @RequestBody Subscription s) {
        return service.update(id, s);
    }

    @PostMapping("/{id}/renew")
    public Subscription renew(@PathVariable Long id, @RequestBody(required = false) RenewalRequest request) {
        return service.renew(id, request == null ? null : request.expectedRenewalDate());
    }
    public record RenewalRequest(java.time.LocalDate expectedRenewalDate) { }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
