package com.renewiq.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import org.springframework.web.bind.annotation.*;

class ApiExceptionHandlerTest {
    @RestController static class FailingController {
        @PostMapping("/test-chat") public void chat() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Add OPENROUTER_API_KEY on the server.");
        }
    }
    @Test void chatFailureExposesSafeReasonInTheFieldTheFrontendReads() throws Exception {
        var mvc = standaloneSetup(new FailingController()).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/test-chat")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Add OPENROUTER_API_KEY on the server."));
    }
}
