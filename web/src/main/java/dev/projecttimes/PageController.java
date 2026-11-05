package dev.projecttimes;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class PageController {
    @GetMapping("/") public String home() { return "index"; }
    @GetMapping("/login") public String login() { return "login"; }
}
