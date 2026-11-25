package com.renewiq;

import com.fasterxml.jackson.databind.*;
import com.renewiq.repository.UserRepository;
import com.renewiq.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "ai.api-key="
})
@AutoConfigureMockMvc
class AccountIsolationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired KnowledgeService knowledge;
    @Autowired com.renewiq.service.AssistantAnalytics analytics;
    record Session(MockHttpSession cookie, String csrf) { }
    @Test void containerHealthIsPublicButDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
            .andExpect(jsonPath("status").value("UP")).andExpect(jsonPath("components").doesNotExist());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
    private Session fresh() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) response.getRequest().getSession(false),
                json.readTree(response.getResponse().getContentAsString()).path("token").asText());
    }
    private Session refresh(MockHttpSession cookie) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session(cookie)).andExpect(status().isOk()).andReturn();
        return new Session(cookie, json.readTree(response.getResponse().getContentAsString()).path("token").asText());
    }
    private Session signup(String email) throws Exception {
        var session = fresh();
        mvc.perform(post("/api/auth/signup").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("name", "Test User", "email", email, "password", "SecretPass123!"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("passwordHash").doesNotExist());
        return refresh(session.cookie());
    }
    @Test void signupLoginValidationAndLogoutUsePersistedCredentials() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        Session session = signup(email.toUpperCase());
        var stored = users.findByEmail(email).orElseThrow();
        assertNotEquals("SecretPass123!", stored.getPasswordHash());
        assertTrue(passwords.matches("SecretPass123!", stored.getPasswordHash()));
        mvc.perform(get("/api/auth/me").session(session.cookie())).andExpect(status().isOk()).andExpect(jsonPath("email").value(email));
        mvc.perform(post("/api/auth/signup").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("name", "Other", "email", email, "password", "SecretPass123!"))))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/logout").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/subscriptions")).andExpect(status().isUnauthorized());
        Session login = fresh();
        mvc.perform(post("/api/auth/login").session(login.cookie()).header("X-CSRF-TOKEN", login.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", email, "password", "WrongPassword"))))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").session(login.cookie()).header("X-CSRF-TOKEN", login.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", email, "password", "SecretPass123!"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(login.cookie())).andExpect(status().isOk());
    }
    @Test void usersCannotReadOrMutateEachOthersSubscriptionsDocumentsOrAssistantActions() throws Exception {
        Session a = signup("a-" + UUID.randomUUID() + "@example.com");
        Session b = signup("b-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/subscriptions").session(a.cookie())).andExpect(content().json("[]"));
        String body = "{\"name\":\"Private plan\",\"category\":\"Testing\",\"amount\":120,\"billingCycle\":\"MONTHLY\",\"nextRenewalDate\":\"2027-01-01\",\"status\":\"ACTIVE\",\"user\":{\"id\":999}}";
        var created = mvc.perform(post("/api/subscriptions").session(a.cookie()).header("X-CSRF-TOKEN", a.csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/subscriptions").session(a.cookie())).andExpect(jsonPath("$[0].name").value("Private plan"));
        mvc.perform(get("/api/subscriptions").session(b.cookie())).andExpect(content().json("[]"));
        mvc.perform(get("/api/dashboard/summary").session(b.cookie())).andExpect(jsonPath("activeCount").value(0));
        mvc.perform(get("/api/subscriptions/upcoming?days=365").session(b.cookie())).andExpect(content().json("[]"));
        mvc.perform(get("/api/subscriptions/" + id).session(b.cookie())).andExpect(status().isNotFound());
        mvc.perform(put("/api/subscriptions/" + id).session(b.cookie()).header("X-CSRF-TOKEN", b.csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/subscriptions/" + id).session(b.cookie()).header("X-CSRF-TOKEN", b.csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/subscriptions/" + id + "/renew").session(b.cookie()).header("X-CSRF-TOKEN", b.csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/assistant/confirm").session(b.cookie()).header("X-CSRF-TOKEN", b.csrf()).contentType("application/json")
                .content("{\"subscriptionId\":" + id + ",\"status\":\"CANCELLED\"}")).andExpect(status().isNotFound());
        var uploaded = mvc.perform(multipart("/api/knowledge").file(new MockMultipartFile("file", "private.txt", "text/plain", "Secret cancellation terms".getBytes()))
                .session(a.cookie()).header("X-CSRF-TOKEN", a.csrf())).andExpect(status().isCreated()).andReturn();
        long documentId = json.readTree(uploaded.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/knowledge").session(b.cookie())).andExpect(content().json("[]"));
        mvc.perform(delete("/api/knowledge/" + documentId).session(b.cookie()).header("X-CSRF-TOKEN", b.csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/knowledge").session(a.cookie())).andExpect(jsonPath("$[0].name").value("private.txt"));
        var context = (org.springframework.security.core.context.SecurityContext) b.cookie().getAttribute("SPRING_SECURITY_CONTEXT");
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            assertTrue(knowledge.search("Secret cancellation").isEmpty());
            assertTrue(analytics.chart("categories", 30, 6, "").points().isEmpty());
        }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        mvc.perform(post("/api/subscriptions").session(a.cookie()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assistant/chat").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"messages\":[{\"role\":\"user\",\"content\":\"Show subscriptions\"}]}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsInvalidRegistrationAndRotatesCsrfAfterLogin() throws Exception {
        var session = fresh();
        mvc.perform(post("/api/auth/signup").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())
                .contentType("application/json").content("{\"name\":\"Test\",\"email\":\"invalid\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("message").exists());
        String email = "csrf-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/signup").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("name", "Test", "email", email, "password", "SecretPass123!"))))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/logout").session(session.cookie()).header("X-CSRF-TOKEN", session.csrf())).andExpect(status().isForbidden());
        var renewed = refresh(session.cookie());
        assertNotEquals(session.csrf(), renewed.csrf());
        mvc.perform(post("/api/auth/logout").session(renewed.cookie()).header("X-CSRF-TOKEN", renewed.csrf())).andExpect(status().isNoContent());
    }
}
