package dev.projecttimes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.bootstrap.enabled=false","spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureMockMvc
class ProductionTransportTest {
    @Autowired MockMvc mvc;
    @MockitoBean Database database;
    @MockitoBean AnalysisService analysis;
    @MockitoBean UserDetailsService users;
    @MockitoBean LoginAttemptStore attempts;
    @MockitoBean SecurityAudit audit;
    @Test void defaultProfileRequiresHttps() throws Exception { mvc.perform(get("/login")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://localhost/login")); }
    @Test void secureResponsesHaveHsts() throws Exception { mvc.perform(get("/login").secure(true)).andExpect(header().exists("Strict-Transport-Security")); }
}
