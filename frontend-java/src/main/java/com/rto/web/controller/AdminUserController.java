package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final ApiClient api;
    private final UserSession session;

    public AdminUserController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    @GetMapping
    public String listUsers(
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "15") int pageSize,
            Model model) {
        requireAuth();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", pageSize);
        if (isActive != null) query.put("is_active", isActive);
        if (search != null && !search.isBlank()) query.put("search", search.trim());

        JsonNode response = api.get("/users", query);
        model.addAttribute("page", response);
        model.addAttribute("isActive", isActive);
        model.addAttribute("search", search);

        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newUserForm(
            @RequestParam(name = "person_id", required = false) Long personId,
            Model model) {
        requireAuth();

        model.addAttribute("prePersonId", personId);

        try {
            model.addAttribute("roles", api.get("/roles"));
        } catch (Exception ignored) {}

        return "admin/users/form";
    }

    @PostMapping
    public String createUser(
            @RequestParam("person_id") Long personId,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(name = "role_ids", required = false) List<Long> roleIds,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("person_id", personId);
            body.put("username", username.trim());
            body.put("password", password.trim());
            body.put("role_ids", roleIds != null ? roleIds : List.of());

            JsonNode created = api.post("/users", body);
            long id = created.path("user_id").asLong();
            ra.addFlashAttribute("flash", "User account created successfully for @" + username);
            return "redirect:/admin/users/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/admin/users/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form input: " + e.getMessage());
            return "redirect:/admin/users/new";
        }
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode user = api.get("/users/" + id);
        model.addAttribute("targetUser", user);

        try {
            model.addAttribute("allRoles", api.get("/roles"));
        } catch (Exception ignored) {}

        try {
            long personId = user.path("person_id").asLong();
            if (personId > 0) {
                model.addAttribute("person", api.get("/persons/" + personId));
            }
        } catch (Exception ignored) {}

        return "admin/users/detail";
    }

    @PostMapping("/{id}/roles")
    public String updateUserRoles(
            @PathVariable Long id,
            @RequestParam(name = "role_ids", required = false) List<Long> roleIds,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("role_ids", roleIds != null ? roleIds : List.of());
            api.put("/users/" + id + "/roles", body);
            ra.addFlashAttribute("flash", "User roles updated successfully");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/status")
    public String toggleUserStatus(
            @PathVariable Long id,
            @RequestParam("is_active") boolean isActive,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/users/" + id, Map.of("is_active", isActive));
            ra.addFlashAttribute("flash", "User account status set to: " + (isActive ? "ACTIVE" : "INACTIVE"));
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/password")
    public String resetUserPassword(
            @PathVariable Long id,
            @RequestParam("password") String newPassword,
            RedirectAttributes ra) {
        requireAuth();

        try {
            api.patch("/users/" + id, Map.of("password", newPassword.trim()));
            ra.addFlashAttribute("flash", "User password has been reset successfully");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }
}
