package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.util.Forms;
import com.rto.web.util.Paging;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/citizens")
public class CitizenController {
    private static final String[] CREATE_FIELDS = {"first_name", "last_name", "date_of_birth", "gender",
            "national_id_number", "phone_primary", "phone_secondary", "email", "citizen_code"};
    private static final String[] ADDRESS_FIELDS = {"line1", "line2", "city", "state", "pincode", "address_type"};
    private static final String[] PATCH_REQUIRED = {"first_name", "last_name", "date_of_birth", "gender",
            "national_id_number", "phone_primary"};
    private static final String[] PATCH_OPTIONAL = {"phone_secondary", "email"};

    private final ApiClient api;

    public CitizenController(ApiClient api) {
        this.api = api;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String search,
                @RequestParam(required = false) String blacklisted, Model model) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search);
        filters.put("blacklisted", blacklisted);
        Map<String, Object> query = new LinkedHashMap<>(filters);
        query.put("page", page);
        query.put("page_size", 15);
        model.addAttribute("result", api.get("/citizens", query));
        model.addAttribute("pagerBase", Paging.base("/citizens", filters));
        model.addAttribute("search", search);
        model.addAttribute("blacklisted", blacklisted);
        return "citizens/list";
    }

    @GetMapping("/new")
    String newForm(Model model) {
        formDefaults(model, new LinkedHashMap<>(), Map.of(), null, "/citizens", "Register citizen", false);
        return "citizens/form";
    }

    @PostMapping
    String create(@RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        Map<String, Object> body = Forms.compact(params, CREATE_FIELDS);
        Map<String, Object> address = new LinkedHashMap<>();
        for (String f : ADDRESS_FIELDS) {
            String v = params.get("address." + f);
            if (v != null && !v.isBlank()) {
                address.put(f, v.trim());
            }
        }
        if (address.containsKey("line1")) {
            body.put("address", address);
        }
        try {
            JsonNode created = api.post("/citizens", body);
            ra.addFlashAttribute("flash", "Citizen " + created.path("citizen_code").asText() + " registered.");
            return "redirect:/citizens/" + created.path("citizen_id").asLong();
        } catch (ApiException e) {
            if (e.status() == 401) {
                throw e;
            }
            formDefaults(model, params, Forms.fieldErrors(e.errors()), e.getMessage(), "/citizens", "Register citizen", false);
            return "citizens/form";
        }
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("c", api.get("/citizens/" + id));
        model.addAttribute("addresses", api.get("/citizens/" + id + "/addresses"));
        return "citizens/detail";
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        JsonNode c = api.get("/citizens/" + id);
        Map<String, String> form = new LinkedHashMap<>();
        for (String k : new String[]{"first_name", "last_name", "date_of_birth", "gender", "national_id_number",
                "phone_primary", "phone_secondary", "email", "blacklist_reason"}) {
            form.put(k, c.path(k).isNull() ? "" : c.path(k).asText(""));
        }
        form.put("blacklisted", c.path("blacklisted").asBoolean() ? "true" : "");
        model.addAttribute("c", c);
        formDefaults(model, form, Map.of(), null, "/citizens/" + id + "/edit", "Edit citizen", true);
        return "citizens/form";
    }

    @PostMapping("/{id}/edit")
    String update(@PathVariable long id, @RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        Map<String, Object> body = Forms.patch(params, PATCH_REQUIRED, PATCH_OPTIONAL);
        boolean blacklisted = "true".equals(params.get("blacklisted"));
        body.put("blacklisted", blacklisted);
        String reason = params.get("blacklist_reason");
        body.put("blacklist_reason", blacklisted && reason != null && !reason.isBlank() ? reason.trim() : null);
        try {
            api.patch("/citizens/" + id, body);
            ra.addFlashAttribute("flash", "Citizen updated.");
            return "redirect:/citizens/" + id;
        } catch (ApiException e) {
            if (e.status() == 401) {
                throw e;
            }
            model.addAttribute("c", api.get("/citizens/" + id));
            formDefaults(model, params, Forms.fieldErrors(e.errors()), e.getMessage(), "/citizens/" + id + "/edit", "Edit citizen", true);
            return "citizens/form";
        }
    }

    @PostMapping("/{id}/addresses")
    String addAddress(@PathVariable long id, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<String, Object> body = Forms.compact(params, ADDRESS_FIELDS);
        try {
            api.post("/citizens/" + id + "/addresses", body);
            ra.addFlashAttribute("flash", "Address added.");
        } catch (ApiException e) {
            if (e.status() == 401) {
                throw e;
            }
            ra.addFlashAttribute("flashError", e.getMessage() + errorSuffix(e));
        }
        return "redirect:/citizens/" + id;
    }

    private static String errorSuffix(ApiException e) {
        Map<String, String> fe = Forms.fieldErrors(e.errors());
        if (fe.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(": ");
        fe.forEach((k, v) -> sb.append(k).append(" ").append(v).append("; "));
        return sb.toString();
    }

    private static void formDefaults(Model model, Map<String, String> form, Map<String, String> fieldErrors,
                                     String formError, String action, String title, boolean editing) {
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("formError", fieldErrors.isEmpty() ? formError : "Please fix the highlighted fields.");
        model.addAttribute("action", action);
        model.addAttribute("formTitle", title);
        model.addAttribute("editing", editing);
    }
}
