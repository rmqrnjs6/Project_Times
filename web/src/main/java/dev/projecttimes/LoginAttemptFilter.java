package dev.projecttimes;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class LoginAttemptFilter extends OncePerRequestFilter {
    private final LoginAttemptStore attempts;
    public LoginAttemptFilter(LoginAttemptStore attempts) { this.attempts=attempts; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().equals(request.getContextPath()+"/login") || !request.getMethod().equals("POST"); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try {
            if (!attempts.allow(request.getRemoteAddr())) {
                response.setStatus(429); response.setHeader("Retry-After","900"); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"login_temporarily_limited\"}"); return;
            }
        } catch (Exception e) { response.sendError(503); return; }
        chain.doFilter(request,response);
    }
}
