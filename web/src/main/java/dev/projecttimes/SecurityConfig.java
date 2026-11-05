package dev.projecttimes;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import javax.sql.DataSource;
import jakarta.servlet.DispatcherType;
@Configuration
public class SecurityConfig {
    @Bean org.springframework.boot.web.servlet.ServletListenerRegistrationBean<org.springframework.security.web.session.HttpSessionEventPublisher> sessionEvents() {
        return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(new org.springframework.security.web.session.HttpSessionEventPublisher());
    }
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(DataSource source) {
        var users=new JdbcUserDetailsManager(source);
        users.setUsersByUsernameQuery("SELECT username,password,enabled FROM security_users WHERE username=?");
        users.setAuthoritiesByUsernameQuery("SELECT username,authority FROM security_authorities WHERE username=?");
        return users;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,Environment environment,LoginAttemptStore attempts,SecurityAudit audit) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(request -> request.getDispatcherType()==DispatcherType.FORWARD && request.getRequestURI().startsWith(request.getContextPath()+"/WEB-INF/views/")).permitAll()
            .requestMatchers("/login","/ready","/api/csrf","/style.css","/workbench.js").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/**").hasAnyRole("USER","ADMIN")
            .requestMatchers("/").authenticated().anyRequest().denyAll());
        http.formLogin(login -> login.loginPage("/login")
            .successHandler((request,response,authentication) -> {
                try { attempts.reset(request.getRemoteAddr()); audit.record("LOGIN_SUCCESS",authentication.getName()); response.sendRedirect("/"); }
                catch (Exception e) { request.getSession().invalidate(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); response.sendError(503); }
            })
            .failureHandler((request,response,error) -> {
                try { audit.record("LOGIN_FAILURE","not-recorded"); response.sendRedirect("/login?error"); }
                catch (Exception e) { response.sendError(503); }
            }).permitAll());
        http.logout(logout -> logout.logoutUrl("/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID").logoutSuccessUrl("/login?logout"));
        http.exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,error) -> {
            if (request.getRequestURI().startsWith(request.getContextPath()+"/api/")) {
                response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"authentication_required\"}");
            } else response.sendRedirect("/login");
        }));
        http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()).maximumSessions(1).expiredSessionStrategy(event -> {
            var request=event.getRequest(); var response=event.getResponse();
            if (request.getRequestURI().startsWith(request.getContextPath()+"/api/")) {
                response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"session_expired\"}");
            } else response.sendRedirect("/login?expired");
        }));
        http.requestCache(cache -> cache.disable());
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'"))
            .frameOptions(frame -> frame.deny()).referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        if (!environment.matchesProfiles("local")) http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        http.addFilterBefore(new RequestSizeFilter(),CsrfFilter.class);
        http.addFilterBefore(new LoginAttemptFilter(attempts),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
