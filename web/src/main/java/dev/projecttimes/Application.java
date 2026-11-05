package dev.projecttimes;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;

@WebListener
public final class Application implements ServletContextListener {
    public void contextInitialized(ServletContextEvent event) {
        var database = new Database();
        database.migrate();
        event.getServletContext().setAttribute("database", database);
        event.getServletContext().setAttribute("analysis", new AnalysisService());
    }
}
