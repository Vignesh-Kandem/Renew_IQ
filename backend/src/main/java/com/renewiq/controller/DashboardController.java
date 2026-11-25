package com.renewiq.controller;

import com.renewiq.dto.DashboardSummary;
import com.renewiq.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SubscriptionService service;

    @GetMapping("/summary")
    public DashboardSummary summary() { return service.summary(); }
}
