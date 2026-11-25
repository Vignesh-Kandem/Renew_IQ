package com.renewiq;

import com.fasterxml.jackson.databind.*;
import com.renewiq.dto.*;
import com.renewiq.service.AssistantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:history;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class ConversationPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AssistantService assistant;
    record Session(MockHttpSession cookie,String token) { }
    @BeforeEach void answer(){when(assistant.chat(anyList(),anyList())).thenReturn(new AssistantResponse("Saved answer",null,List.of(),List.of()));}
    private Session account()throws Exception{
        var fresh=mvc.perform(get("/api/auth/csrf")).andReturn();
        var cookie=(MockHttpSession)fresh.getRequest().getSession(false);
        String token=json.readTree(fresh.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(post("/api/auth/signup").session(cookie).header("X-CSRF-TOKEN",token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","History Test","email",UUID.randomUUID()+"@example.com","password","History-test-123!"))))
                .andExpect(status().isCreated());
        var refreshed=mvc.perform(get("/api/auth/csrf").session(cookie)).andReturn();
        return new Session(cookie,json.readTree(refreshed.getResponse().getContentAsString()).path("token").asText());
    }
    private long create(Session s)throws Exception{
        var result=mvc.perform(post("/api/assistant/conversations").session(s.cookie()).header("X-CSRF-TOKEN",s.token())).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }
    private JsonNode send(Session s,long id,String content,String requestId,List<Long> documents)throws Exception{
        var result=mvc.perform(post("/api/assistant/conversations/"+id+"/messages").session(s.cookie()).header("X-CSRF-TOKEN",s.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("content",content,"requestId",requestId,"documentIds",documents))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void savesReopensContinuesAndDeduplicatesTurns()throws Exception{
        var a=account();long id=create(a);String requestId=UUID.randomUUID().toString();
        var first=send(a,id,"Remember my subscriptions",requestId,List.of());
        assertEquals(2,first.path("messages").size());
        assertEquals(2,send(a,id,"Remember my subscriptions",requestId,List.of()).path("messages").size());
        verify(assistant,times(1)).chat(anyList(),anyList());
        mvc.perform(get("/api/assistant/conversations/"+id).session(a.cookie())).andExpect(jsonPath("$.messages[1].content").value("Saved answer"));
        mvc.perform(get("/api/assistant/conversations").session(a.cookie())).andExpect(jsonPath("$[0].title").value("Remember my subscriptions"));
        send(a,id,"What did I just ask?",UUID.randomUUID().toString(),List.of());
        org.mockito.ArgumentCaptor<List<AssistantRequest.Msg>> captor=org.mockito.ArgumentCaptor.forClass((Class)List.class);
        verify(assistant,times(2)).chat(captor.capture(),anyList());
        assertEquals(3,captor.getValue().size());
        assertEquals("Remember my subscriptions",captor.getValue().get(0).content());
        assertEquals("Saved answer",captor.getValue().get(1).content());
    }
    @Test void failedProviderReplyStillPreservesTheUsersMessage()throws Exception{
        var a=account();long id=create(a);
        when(assistant.chat(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI is unavailable."));
        var view=send(a,id,"Do not lose this message",UUID.randomUUID().toString(),List.of());
        assertEquals("Do not lose this message",view.path("messages").path(0).path("content").asText());
        assertTrue(view.path("messages").path(1).path("metadata").path("error").asBoolean());
        assertFalse(view.path("processing").asBoolean());
        mvc.perform(get("/api/assistant/conversations/"+id).session(a.cookie())).andExpect(jsonPath("$.messages.length()").value(2));
    }
    @Test void conversationEndpointsRejectAnotherAccount()throws Exception{
        var a=account();var b=account();long id=create(a);
        var view=send(a,id,"Private history",UUID.randomUUID().toString(),List.of());
        long messageId=view.path("messages").path(1).path("id").asLong();
        mvc.perform(get("/api/assistant/conversations").session(b.cookie())).andExpect(content().json("[]"));
        mvc.perform(get("/api/assistant/conversations/"+id).session(b.cookie())).andExpect(status().isNotFound());
        mvc.perform(post("/api/assistant/conversations/"+id+"/messages").session(b.cookie()).header("X-CSRF-TOKEN",b.token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("content","Intrusion","requestId",UUID.randomUUID().toString(),"documentIds",List.of())))).andExpect(status().isNotFound());
        mvc.perform(post("/api/assistant/conversations/"+id+"/messages/"+messageId+"/action").session(b.cookie()).header("X-CSRF-TOKEN",b.token()).contentType("application/json").content("{\"confirm\":true}")).andExpect(status().isNotFound());
    }
    @Test void preservesChartsReferencesAndAttachmentNames()throws Exception{
        var a=account();long id=create(a);
        var upload=mvc.perform(multipart("/api/knowledge").file(new MockMultipartFile("file","policy.md","text/plain","Sample policy".getBytes())).session(a.cookie()).header("X-CSRF-TOKEN",a.token())).andReturn();
        long documentId=json.readTree(upload.getResponse().getContentAsString()).path("id").asLong();
        var chart=new AssistantChart("bar","Spending","INR","Category","INR",LocalDate.now(),"Snapshot",List.of(new AssistantChart.Point("Music",new BigDecimal("100"))));
        var source=new com.renewiq.service.KnowledgeService.Passage(documentId,"policy.md",1,"Sample policy",1);
        when(assistant.chat(anyList(),anyList())).thenReturn(new AssistantResponse("Chart answer",null,List.of(source),List.of(chart)));
        send(a,id,"Show a chart",UUID.randomUUID().toString(),List.of(documentId));
        mvc.perform(get("/api/assistant/conversations/"+id).session(a.cookie()))
                .andExpect(jsonPath("$.messages[0].metadata.attachments[0].name").value("policy.md"))
                .andExpect(jsonPath("$.messages[1].metadata.charts[0].points[0].value").value(100))
                .andExpect(jsonPath("$.messages[1].metadata.sources[0].name").value("policy.md"))
                .andExpect(jsonPath("$.documentIds[0]").value(documentId));
    }
    @Test void confirmedActionStateSurvivesReopeningAndIsNotRepeated()throws Exception{
        var a=account();long id=create(a);
        var plan=mvc.perform(post("/api/subscriptions").session(a.cookie()).header("X-CSRF-TOKEN",a.token()).contentType("application/json")
                .content("{\"name\":\"Test plan\",\"category\":\"Testing\",\"amount\":100,\"billingCycle\":\"MONTHLY\",\"nextRenewalDate\":\"2027-01-01\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn();
        long subscriptionId=json.readTree(plan.getResponse().getContentAsString()).path("id").asLong();
        when(assistant.chat(anyList(),anyList())).thenReturn(new AssistantResponse("Confirm to pause",new AssistantResponse.PendingAction(subscriptionId,"Test plan","PAUSED"),List.of(),List.of()));
        var view=send(a,id,"Pause the plan",UUID.randomUUID().toString(),List.of());
        long messageId=view.path("messages").path(1).path("id").asLong();
        String path="/api/assistant/conversations/"+id+"/messages/"+messageId+"/action";
        mvc.perform(post(path).session(a.cookie()).header("X-CSRF-TOKEN",a.token()).contentType("application/json").content("{\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].metadata.actionState").value("done"));
        mvc.perform(get("/api/subscriptions/"+subscriptionId).session(a.cookie())).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(get("/api/assistant/conversations/"+id).session(a.cookie())).andExpect(jsonPath("$.messages[1].metadata.actionState").value("done"));
        mvc.perform(post(path).session(a.cookie()).header("X-CSRF-TOKEN",a.token()).contentType("application/json").content("{\"confirm\":false}"))
                .andExpect(jsonPath("$.messages[1].metadata.actionState").value("done"));
    }
    @Test void separateChatsKeepTheirOwnMessagesAndRenamedTitles()throws Exception{
        var a=account();long first=create(a),second=create(a);
        send(a,first,"First chat topic",UUID.randomUUID().toString(),List.of());
        var other=send(a,second,"Second chat topic",UUID.randomUUID().toString(),List.of());
        assertEquals(2,other.path("messages").size());
        assertEquals("Second chat topic",other.path("title").asText());
        mvc.perform(patch("/api/assistant/conversations/"+first).session(a.cookie()).header("X-CSRF-TOKEN",a.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("title","My budget"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("My budget"));
        var continued=send(a,first,"Continue first",UUID.randomUUID().toString(),List.of());
        assertEquals("My budget",continued.path("title").asText());
        assertEquals(4,continued.path("messages").size());
        mvc.perform(get("/api/assistant/conversations/"+second).session(a.cookie()))
                .andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.title").value("Second chat topic"));
        var b=account();
        mvc.perform(patch("/api/assistant/conversations/"+first).session(b.cookie()).header("X-CSRF-TOKEN",b.token())
                .contentType("application/json").content("{\"title\":\"Intrusion\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/assistant/conversations/"+first).session(a.cookie()).header("X-CSRF-TOKEN",a.token())
                .contentType("application/json").content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void deletesOnlyOwnedChatAndAllItsMessages()throws Exception{
        var a=account();var b=account();long id=create(a),keep=create(a);
        send(a,id,"Delete this history",UUID.randomUUID().toString(),List.of());
        send(a,keep,"Keep this history",UUID.randomUUID().toString(),List.of());
        mvc.perform(delete("/api/assistant/conversations/"+id).session(b.cookie()).header("X-CSRF-TOKEN",b.token()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/assistant/conversations/"+id).session(a.cookie())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/assistant/conversations/"+id).session(a.cookie()).header("X-CSRF-TOKEN",a.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/assistant/conversations/"+id).session(a.cookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/assistant/conversations").session(a.cookie())).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/assistant/conversations/"+keep).session(a.cookie())).andExpect(jsonPath("$.messages.length()").value(2));
    }

}
