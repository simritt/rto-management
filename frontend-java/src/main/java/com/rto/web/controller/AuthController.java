package com.rto.web.controller;

import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.ResourceAccessException;

@Controller
public class AuthController {
    private final ApiClient api;
    private final UserSession session;

    public AuthController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping("/login")
    String loginForm(@RequestParam(required = false) String expired, Model model) {
        if (session.isLoggedIn()) {
            return "redirect:/";
        }
        if (expired != null) {
            model.addAttribute("error", "Your session expired. Please sign in again.");
        }
        return "login";
    }

    @PostMapping("/login")
    String login(@RequestParam String username, @RequestParam String password, Model model) {
        try {
            api.login(username.trim(), password);
            return "redirect:/";
        } catch (ApiException e) {
            model.addAttribute("error", e.status() == 401 || e.status() == 403 ? "Invalid username or password." : e.getMessage());
        } catch (ResourceAccessException e) {
            model.addAttribute("error", "Cannot reach the RTO backend API. Is it running on port 8000?");
        }
        model.addAttribute("username", username);
        return "login";
    }

    @PostMapping("/logout")
    String logout() {
        api.logout();
        return "redirect:/login";
    }
}
