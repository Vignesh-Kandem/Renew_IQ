package com.renewiq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renewiq.dto.AssistantRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final List<AssistantRequest.Msg> history = List.of(new AssistantRequest.Msg("user", "Netflix cancellation notice?"));

    @Test void missingKeyIsActionableWithoutCallingProvider() {
        var service = new AssistantService(subscriptions, mapper, "", "test", "http://localhost:1", knowledge);
        var error = assertThrows(ResponseStatusException.class, () -> service.chat(history));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("GROQ_API_KEY"));
        verifyNoInteractions(knowledge);
    }

    @Test void retrievedExcerptsReachProviderAndAreReturnedAsReferences() throws Exception {
        var passage = new KnowledgeService.Passage(4L, "policy.md", 1, "Netflix cancellation requires 30 days notice.", 1.0);
        when(knowledge.search(anyString())).thenReturn(List.of(passage));
        AtomicReference<String> request = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"30 days [document 4, chunk 1]\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var service = new AssistantService(subscriptions, mapper, "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), knowledge);
            var response = service.chat(history);
            assertTrue(response.reply().contains("30 days"));
            assertEquals(List.of(passage), response.sources());
            var sent = mapper.readTree(request.get());
            assertTrue(sent.path("messages").toString().contains("untrusted reference data"));
            assertTrue(sent.path("messages").toString().contains("Netflix cancellation requires 30 days"));
            assertEquals(5, sent.path("tools").size());
            assertEquals("test-model", sent.path("model").asText());
            assertEquals(4096, sent.path("max_tokens").asInt());
        } finally { server.stop(0); }
    }

    @Test void modelRequestsChartButValuesAreCalculatedFromAccountData() throws Exception {
        var plan = new com.renewiq.model.Subscription();
        plan.setId(1L); plan.setName("Example"); plan.setCategory("Music");
        plan.setStatus(com.renewiq.model.SubscriptionStatus.ACTIVE);
        plan.setBillingCycle(com.renewiq.model.BillingCycle.YEARLY);
        plan.setAmount(new java.math.BigDecimal("1200"));
        when(subscriptions.findAll()).thenReturn(List.of(plan));
        var turns = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> followup = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean first = turns.getAndIncrement() == 0;
            if (!first) followup.set(body);
            String answer = first
                    ? "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"chart1\",\"type\":\"function\",\"function\":{\"name\":\"show_chart\",\"arguments\":\"{\\\"metric\\\":\\\"categories\\\"}\"}}]}}]}"
                    : "{\"choices\":[{\"message\":{\"content\":\"Music costs 100 INR per month.\"}}]}";
            byte[] response = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var service = new AssistantService(subscriptions, mapper, "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), knowledge);
            var response = service.chat(history);
            assertEquals(1, response.charts().size());
            assertEquals(0, new java.math.BigDecimal("100").compareTo(response.charts().get(0).points().get(0).value()));
            assertTrue(followup.get().contains("Monthly spending by category"));
            assertFalse(followup.get().contains("Error: could not read the data"));
        } finally { server.stop(0); }
    }
    @Test void quotaErrorsExplainResetInsteadOfOpenRouterCredits() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var service = new AssistantService(subscriptions, mapper, "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), knowledge);
            var error = assertThrows(ResponseStatusException.class, () -> service.chat(history));
            assertTrue(error.getReason().contains("quota to reset"));
            assertFalse(error.getReason().contains("OpenRouter"));
        } finally { server.stop(0); }
    }

}
