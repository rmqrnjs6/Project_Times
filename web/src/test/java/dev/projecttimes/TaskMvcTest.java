package dev.projecttimes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.bootstrap.enabled=false","spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TaskMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean Database database;
    @MockitoBean AnalysisService analysis;
    @MockitoBean UserDetailsService users;
    @MockitoBean LoginAttemptStore attempts;
    @MockitoBean SecurityAudit audit;
    @MockitoBean TaskStore store;
    final String id="812cba03-a324-4df3-9808-e7fc1e257332";
    @Test void anonymousCannotReadTasks() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized()); verifyNoInteractions(store);
    }
    @Test void csrfRequiredForCreateAndCancel() throws Exception {
        mvc.perform(post("/api/tasks").with(user("alice")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/"+id+"/cancel").with(user("alice")).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
    @Test void forgedOwnerAndStatusCannotOverrideServer() throws Exception {
        when(store.create(anyString(),anyString(),anyString())).thenReturn(UUID.fromString(id));
        mvc.perform(post("/api/tasks").with(user("alice")).with(csrf()).contentType("application/json")
            .content("{\"question\":\" synthetic \",\"evidenceRequirement\":\"PRIMARY_REQUIRED\",\"owner\":\"bob\",\"status\":\"SUCCEEDED\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/tasks/"+id)).andExpect(jsonPath("$.status").value("QUEUED"));
        verify(store).create("alice","synthetic","PRIMARY_REQUIRED"); verifyNoInteractions(analysis);
    }
    @Test void invalidInputRejectedBeforeStorage() throws Exception {
        for(String body:List.of("{}","{\"question\":\" \",\"evidenceRequirement\":\"PRIMARY_REQUIRED\"}","{\"question\":\"test\",\"evidenceRequirement\":\"INVALID\"}")) {
            mvc.perform(post("/api/tasks").with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/tasks/not-a-uuid").with(user("alice"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks/"+id+"/cancel").with(user("alice")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
    @Test void detailUsesAuthenticatedOwner() throws Exception {
        when(store.detail("alice",UUID.fromString(id))).thenThrow(new RequestFailure(404,"task_not_found"));
        mvc.perform(get("/api/tasks/"+id+"?owner=bob").with(user("alice"))).andExpect(status().isNotFound());
        verify(store).detail("alice",UUID.fromString(id));
    }
    @Test void cancelUsesExpectedVersionAndReturnsNoContent() throws Exception {
        mvc.perform(post("/api/tasks/"+id+"/cancel").with(user("alice")).with(csrf()).contentType("application/json").content("{\"version\":2,\"owner\":\"bob\"}")).andExpect(status().isNoContent());
        verify(store).cancel("alice",UUID.fromString(id),2);
    }
    @Test void workerTransitionsAreNotPublicEndpoints() throws Exception {
        mvc.perform(post("/api/tasks/"+id+"/complete").with(user("alice")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        verifyNoInteractions(store);
    }

    @Test void manualNoteUsesSessionOwnerAndKeepsOriginalSeparate() throws Exception {
        when(store.addNote(anyString(),any(),anyString(),anyString(),anyString(),anyString())).thenReturn(UUID.randomUUID());
        mvc.perform(post("/api/tasks/"+id+"/notes").with(user("alice")).with(csrf()).contentType("application/json")
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("sourceUrl","https://example.org/source","originalText"," original ","interpretation","interpretation","stance","UNVERIFIED","owner","bob"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.trusted").value(false)).andExpect(jsonPath("$.fetched").value(false));
        verify(store).addNote("alice",UUID.fromString(id),"https://example.org/source"," original ","interpretation","UNVERIFIED");
        verifyNoInteractions(analysis);
    }
    @Test void invalidNoteAndSensitiveUrlRejected() throws Exception {
        for(String source:List.of("http://example.org","javascript:alert(1)","https://user:secret@example.org","https://example.org/?token=secret")) {
            String body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("sourceUrl",source,"originalText","text","interpretation","reason","stance","SUPPORTS"));
            mvc.perform(post("/api/tasks/"+id+"/notes").with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/tasks/"+id+"/notes").with(user("alice")).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
    @Test void noteRequiresCsrfAndHasNoUpdateEndpoint() throws Exception {
        mvc.perform(post("/api/tasks/"+id+"/notes").with(user("alice")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/"+id+"/notes").with(user("alice")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        verifyNoInteractions(store);
    }
    @Test void exportUsesOwnerAndIsPrivateAttachment() throws Exception {
        when(store.detail("alice",UUID.fromString(id))).thenReturn(Map.of("id",id,"notes",List.of()));
        mvc.perform(get("/api/tasks/"+id+"/export?owner=bob").with(user("alice")))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=verification-task.json"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.schema_version").value("1"));
        verify(store).detail("alice",UUID.fromString(id));
    }
}
