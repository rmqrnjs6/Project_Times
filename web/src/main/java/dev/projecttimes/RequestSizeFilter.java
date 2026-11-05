package dev.projecttimes;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class RequestSizeFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String origin=request.getHeader("Origin");
        boolean unsafe=!java.util.Set.of("GET","HEAD","OPTIONS","TRACE").contains(request.getMethod());
        if (unsafe && origin!=null && !origin.equals(request.getScheme()+"://"+request.getHeader("Host"))) {
            response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"cross_origin_rejected\"}"); return;
        }
        if (request.getContentLengthLong()>32768) { reject(response); return; }
        if (request.getRequestURI().startsWith(request.getContextPath()+"/api/") && request.getMethod().equals("POST")) {
            byte[] body=request.getInputStream().readNBytes(32769);
            if (body.length>32768) { reject(response); return; }
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    var stream=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return stream.read(); }
                        public boolean isFinished() { return stream.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
            },response);
        } else chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response) throws IOException { response.setStatus(413); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"body_too_large\"}"); }
}
