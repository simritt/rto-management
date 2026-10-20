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
@RequestMapping("/admin/roles")
public class AdminRoleController {

    private final ApiClient api;
    private final UserSession session;

    public AdminRoleController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    private void requireAuth() {
        if (!session.isLoggedIn()) {
            throw new RuntimeException("Authentication required");
        }
    }

    @GetMapping
    public String listRoles(Model model) {
        requireAuth();

        JsonNode roles = api.get("/roles");
        model.addAttribute("roles", roles);

        return "admin/roles/list";
    }

    @GetMapping("/new")
    public String newRoleForm(Model model) {
        requireAuth();

        try {
            model.addAttribute("permissions", api.get("/permissions"));
        } catch (Exception ignored) {}

        return "admin/roles/form";
    }

    @PostMapping
    public String createRole(
            @RequestParam("role_name") String roleName,
            @RequestParam(name = "permission_keys", required = false) List<String> permKeys,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("role_name", roleName.trim());
            body.put("permission_keys", permKeys != null ? permKeys : List.of());

            JsonNode created = api.post("/roles", body);
            long id = created.path("role_id").asLong();
            ra.addFlashAttribute("flash", "Role '" + roleName + "' created successfully");
            return "redirect:/admin/roles/" + id;
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/admin/roles/new";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Invalid form data: " + e.getMessage());
            return "redirect:/admin/roles/new";
        }
    }

    @GetMapping("/{id}")
    public String viewRole(@PathVariable Long id, Model model) {
        requireAuth();

        JsonNode role = api.get("/roles/" + id);
        model.addAttribute("role", role);

        try {
            model.addAttribute("allPermissions", api.get("/permissions"));
        } catch (Exception ignored) {}

        return "admin/roles/detail";
    }

    @PostMapping("/{id}/permissions")
    public String updateRolePermissions(
            @PathVariable Long id,
            @RequestParam(name = "permission_keys", required = false) List<String> permKeys,
            RedirectAttributes ra) {
        requireAuth();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("permission_keys", permKeys != null ? permKeys : List.of());
            api.put("/roles/" + id + "/permissions", body);
            ra.addFlashAttribute("flash", "Permissions updated successfully for role");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/roles/" + id;
    }
}
