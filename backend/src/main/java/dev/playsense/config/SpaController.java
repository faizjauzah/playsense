package dev.playsense.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Static export writes one directory per route; these are the only client routes that exist. */
@Controller
public class SpaController {

    @GetMapping({"/login", "/login/"})
    public String login() {
        return "forward:/login/index.html";
    }

    @GetMapping({"/private", "/private/"})
    public String privateLibrary() {
        return "forward:/private/index.html";
    }
}
