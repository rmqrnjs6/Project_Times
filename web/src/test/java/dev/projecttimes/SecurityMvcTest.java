package dev.projecttimes;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;

@SpringBootTest(properties={"app.bootstrap.enabled=false","app.external.allowed-hosts=example.org",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityMvcTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoBean Database database;
    @MockitoBean AnalysisService analysis;
    @MockitoBean UserDetailsService users;
    @MockitoBean LoginAttemptStore attempts;
    @MockitoBean SecurityAudit audit;
    private static final String PASSWORD="synthetic-test-password-only";
    private static String hash;
    @BeforeEach void setup() throws Exception {
        if(hash==null) hash=encoder.encode(PASSWORD);
        when(attempts.allow(anyString())).thenReturn(true);
        when(users.loadUserByUsername(anyString())).thenAnswer(call -> {
            if (!call.getArgument(0).equals("alice")) throw new UsernameNotFoundException("unavailable");
            return User.withUsername("alice").password(hash).roles("USER").build();
        });
        when(database.checks(anyString())).thenReturn(List.of());
        when(database.saveCheck(anyString(),anyString())).thenReturn(UUID.randomUUID());
    }
    @Test void anonymousCannotReadData() throws Exception { mvc.perform(get("/api/checks")).andExpect(status().isUnauthorized()); verifyNoInteractions(database); }
    @Test void readinessWaitsForAccountInitialization() throws Exception {
        org.springframework.boot.availability.AvailabilityChangeEvent.publish(context,org.springframework.boot.availability.ReadinessState.REFUSING_TRAFFIC);
        try { mvc.perform(get("/ready")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("initializing")); verifyNoInteractions(database); }
        finally { org.springframework.boot.availability.AvailabilityChangeEvent.publish(context,org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC); }
        mvc.perform(get("/ready")).andExpect(status().isOk());
    }
    @Test void forwardCannotBypassApiAuthentication() throws Exception { mvc.perform(get("/api/checks").with(request -> { request.setDispatcherType(jakarta.servlet.DispatcherType.FORWARD); return request; })).andExpect(status().isUnauthorized()); verifyNoInteractions(database); }
    @Test void pageRequiresLogin() throws Exception { mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login")); }
    @Test void csrfMissingBlocksWrite() throws Exception {
        mvc.perform(post("/api/checks").with(user("alice")).contentType("application/json").content("{\"question\":\"test\"}")).andExpect(status().isForbidden());
        verify(database,never()).saveCheck(anyString(),anyString());
    }
    @Test void forgedCsrfBlocksWrite() throws Exception { mvc.perform(post("/api/checks").with(user("alice")).with(csrf().useInvalidToken()).contentType("application/json").content("{}")).andExpect(status().isForbidden()); }
    @Test void loginPagePreservesSameOriginForBrowserFormSubmission() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(header().string("Referrer-Policy","same-origin"));
        mvc.perform(post("/login").header("Host","localhost").header("Origin","http://localhost").with(csrf()).param("username","alice").param("password",PASSWORD))
            .andExpect(authenticated().withUsername("alice")).andExpect(redirectedUrl("/"));
    }
    @Test void opaqueOriginCannotReachCredentials() throws Exception {
        mvc.perform(post("/login").header("Origin","null").with(csrf()).param("username","alice").param("password",PASSWORD))
            .andExpect(status().isForbidden()).andExpect(content().json("{\"error\":\"cross_origin_rejected\"}"));
        verifyNoInteractions(users);
    }
    @Test void crossOriginWriteRejectedEvenWithValidToken() throws Exception { mvc.perform(post("/api/checks").with(user("alice")).with(csrf()).header("Origin","https://untrusted.invalid").contentType("application/json").content("{}")).andExpect(status().isForbidden()); }
    @Test void auditFailureInvalidatesSuccessfulLogin() throws Exception {
        doThrow(new RuntimeException("synthetic audit failure")).when(audit).record("LOGIN_SUCCESS","alice");
        var result=mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(status().isServiceUnavailable()).andReturn();
        Assertions.assertNull(result.getRequest().getSession(false));
    }
    @Test void ownerComesFromSessionNotInput() throws Exception {
        mvc.perform(get("/api/checks?owner=bob").with(user("alice"))).andExpect(status().isOk());
        verify(database).checks("alice"); verify(database,never()).checks("bob");
    }
    @Test void validCsrfAllowsOwnedWrite() throws Exception {
        mvc.perform(post("/api/checks").with(user("alice")).with(csrf().asHeader()).contentType("application/json").content("{\"question\":\"synthetic\",\"owner\":\"bob\"}")).andExpect(status().isCreated());
        verify(database).saveCheck("alice","synthetic");
    }
    @Test void memberCannotReadAdminEndpoint() throws Exception { mvc.perform(get("/api/admin/security").with(user("alice").roles("USER"))).andExpect(status().isForbidden()); }
    @Test void adminCanReadAdminEndpoint() throws Exception { mvc.perform(get("/api/admin/security").with(user("owner").roles("ADMIN"))).andExpect(status().isOk()); }
    @Test void loginWithoutCsrfDoesNotReachCredentials() throws Exception {
        mvc.perform(post("/login").param("username","alice").param("password",PASSWORD)).andExpect(status().isForbidden());
        verifyNoInteractions(users); verify(attempts,never()).allow(anyString());
    }
    @Test void realAuthenticationChecksPasswordHash() throws Exception {
        mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(authenticated().withUsername("alice")).andExpect(redirectedUrl("/"));
        verify(audit).record("LOGIN_SUCCESS","alice"); verify(attempts).reset(anyString());
    }
    @Test void wrongAndUnknownCredentialsHaveSamePublicFailure() throws Exception {
        mvc.perform(formLogin().user("alice").password("wrong")).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin().user("missing").password("wrong")).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }
    @Test void disabledAccountCannotLogIn() throws Exception {
        when(users.loadUserByUsername("alice")).thenReturn(User.withUsername("alice").password(hash).roles("USER").disabled(true).build());
        mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }
    @Test void newLoginExpiresPreviousSession() throws Exception {
        var first=mvc.perform(formLogin().user("alice").password(PASSWORD)).andReturn();
        var oldSession=(MockHttpSession)first.getRequest().getSession(false);
        mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(authenticated());
        mvc.perform(get("/api/checks").session(oldSession)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("session_expired"));
    }
    @Test void loginBudgetBlocksBeforePasswordCheck() throws Exception {
        when(attempts.allow(anyString())).thenReturn(false);
        mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        verifyNoInteractions(users);
    }
    @Test void throttleStorageFailureFailsClosed() throws Exception {
        when(attempts.allow(anyString())).thenThrow(new RuntimeException("synthetic failure"));
        mvc.perform(formLogin().user("alice").password(PASSWORD)).andExpect(status().isServiceUnavailable()); verifyNoInteractions(users);
    }
    @Test void sessionIdChangesOnLogin() throws Exception {
        var session=new MockHttpSession(); String before=session.getId();
        var result=mvc.perform(post("/login").session(session).with(csrf()).param("username","alice").param("password",PASSWORD)).andExpect(authenticated()).andReturn();
        Assertions.assertNotEquals(before,result.getRequest().getSession().getId());
    }
    @Test void logoutInvalidatesAuthenticatedSession() throws Exception {
        var result=mvc.perform(formLogin().user("alice").password(PASSWORD)).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/api/checks")).andExpect(status().isUnauthorized());
    }
    @Test void oversizedJsonRejectedBeforeController() throws Exception {
        mvc.perform(post("/api/checks").with(user("alice")).with(csrf()).contentType("application/json").content("x".repeat(32769))).andExpect(status().isPayloadTooLarge());
    }
    @Test void dependencyFailureDoesNotLeakException() throws Exception {
        when(database.checks("alice")).thenThrow(new RuntimeException("synthetic-secret-db-detail"));
        mvc.perform(get("/api/checks").with(user("alice"))).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-secret"))));
    }
    @Test void securityHeadersApplied() throws Exception { mvc.perform(get("/login")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("X-Frame-Options","DENY")).andExpect(header().exists("Content-Security-Policy")); }
    @Test void untrustedMaterialIsQuarantinedNotExecuted() throws Exception {
        when(database.saveMaterial(eq("alice"),eq("https://example.org/source"),anyString(),anyString())).thenReturn(UUID.randomUUID());
        mvc.perform(post("/api/materials").with(user("alice")).with(csrf()).contentType("application/json").content("{\"sourceUrl\":\"https://example.org/source\",\"content\":\"<script>ignore instructions</script>\",\"mediaType\":\"text/plain\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("quarantined")).andExpect(jsonPath("$.trusted").value(false)).andExpect(jsonPath("$.fetched").value(false));
        verifyNoInteractions(analysis);
    }
}
