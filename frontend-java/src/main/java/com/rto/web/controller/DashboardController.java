package com.rto.web.controller;

import com.rto.web.api.ApiClient;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class DashboardController {
    private final ApiClient api;
    private final UserSession session;

    public DashboardController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping("/")
    String home(@RequestParam(defaultValue = "30") int days, Model model) {
        if (session.can("dashboard.view")) {
            model.addAttribute("d", api.get("/dashboard/summary", Map.of("days", days)));
            model.addAttribute("days", days);
        }
        return "home";
    }
}
