package com.rto.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.web.api.ApiClient;
import com.rto.web.api.ApiException;
import com.rto.web.api.UserSession;
import com.rto.web.util.Forms;
import com.rto.web.util.Paging;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/reference")
public class AdminReferenceController {

    private final ApiClient api;
    private final UserSession session;

    public record RefConfig(
            String key,
            String endpoint,
            String title,
            String description,
            String idField,
            String nameField,
            List<FieldDef> fields
    ) {}

    public record FieldDef(
            String name,
            String label,
            String type,       // text, number, decimal, boolean, select_parent_region, select_manufacturer
            boolean required,
            String placeholder,
            String helpText
    ) {}

    private static final Map<String, RefConfig> CONFIGS = new LinkedHashMap<>();

    static {
        CONFIGS.put("regions", new RefConfig(
                "regions", "/regions", "Regions", "Geographical jurisdictions (States, Divisions, Districts)",
                "region_id", "region_name",
                List.of(
                        new FieldDef("region_name", "Region Name", "text", true, "e.g. Karnataka South", "Full name of the regional territory"),
                        new FieldDef("region_code", "Region Code", "text", true, "e.g. KA-S", "Unique uppercase code"),
                        new FieldDef("parent_region_id", "Parent Region", "select_parent_region", false, null, "Optional parent for hierarchical breakdown")
                )
        ));

        CONFIGS.put("service-types", new RefConfig(
                "service-types", "/service-types", "Service Types", "Citizen services offered by the RTO with SLAs and base fees",
                "service_type_id", "service_name",
                List.of(
                        new FieldDef("service_name", "Service Name", "text", true, "e.g. New Driving Licence", "Display title of the service"),
                        new FieldDef("service_code", "Service Code", "text", true, "e.g. DL_NEW", "Unique uppercase alphanumeric code"),
                        new FieldDef("base_fee", "Base Fee (Rs)", "decimal", true, "200.00", "Standard fee in INR before taxes/penalties"),
                        new FieldDef("sla_days", "SLA (Days)", "number", true, "7", "Expected service turnaround days")
                )
        ));

        CONFIGS.put("document-types", new RefConfig(
                "document-types", "/document-types", "Document Types", "Accepted verification documents (Aadhaar, Form 1, Medical Certificate)",
                "document_type_id", "type_name",
                List.of(
                        new FieldDef("type_name", "Document Type Name", "text", true, "e.g. Aadhaar Card / Age Proof", "Formal document title"),
                        new FieldDef("is_mandatory_default", "Mandatory by Default", "boolean", false, null, "Whether applications default to requiring this document"),
                        new FieldDef("validity_period_days", "Validity Period (Days)", "number", false, "365", "Leave blank if document does not expire")
                )
        ));

        CONFIGS.put("vehicle-types", new RefConfig(
                "vehicle-types", "/vehicle-types", "Vehicle Types", "Vehicle categories (Motorcycle, LMV, Heavy Transport)",
                "vehicle_type_id", "type_name",
                List.of(
                        new FieldDef("type_name", "Vehicle Type Name", "text", true, "e.g. Light Motor Vehicle (LMV)", "Classification name"),
                        new FieldDef("is_commercial", "Commercial Vehicle", "boolean", false, null, "Subject to commercial tax, permits, and fitness rules")
                )
        ));

        CONFIGS.put("fuel-types", new RefConfig(
                "fuel-types", "/fuel-types", "Fuel Types", "Propulsion fuels (Petrol, Diesel, Electric, CNG, Hybrid)",
                "fuel_type_id", "fuel_name",
                List.of(
                        new FieldDef("fuel_name", "Fuel Name", "text", true, "e.g. Electric (EV)", "Primary fuel / propulsion category")
                )
        ));

        CONFIGS.put("licence-classes", new RefConfig(
                "licence-classes", "/licence-classes", "Licence Classes", "Driver licence endorsements (MCWG, LMV, TRANS, etc.)",
                "licence_class_id", "class_code",
                List.of(
                        new FieldDef("class_code", "Class Code", "text", true, "e.g. MCWG", "Standard licence endorsement code"),
                        new FieldDef("description", "Description", "text", true, "e.g. Motor cycle with gear", "Detailed explanation of permitted vehicles")
                )
        ));

        CONFIGS.put("violation-types", new RefConfig(
                "violation-types", "/violation-types", "Violation Types", "Traffic infractions with statutory base fines",
                "violation_type_id", "description",
                List.of(
                        new FieldDef("description", "Violation Description", "text", true, "e.g. Driving without a valid seat belt", "Infraction definition"),
                        new FieldDef("base_fine_amount", "Base Fine (Rs)", "decimal", true, "1000.00", "Statutory base penalty in INR"),
                        new FieldDef("is_cognizable", "Cognizable Offence", "boolean", false, null, "Requires mandatory court appearance or impounding")
                )
        ));

        CONFIGS.put("permit-types", new RefConfig(
                "permit-types", "/permit-types", "Permit Types", "Commercial road and route permits (Stage Carriage, Goods Carrier, National Permit)",
                "permit_type_id", "type_name",
                List.of(
                        new FieldDef("type_name", "Permit Type Name", "text", true, "e.g. All India Tourist Permit", "Official permit classification"),
                        new FieldDef("validity_months", "Validity (Months)", "number", true, "60", "Default permit lifespan in months")
                )
        ));

        CONFIGS.put("vehicle-manufacturers", new RefConfig(
                "vehicle-manufacturers", "/vehicle-manufacturers", "Manufacturers", "Vehicle makers and OEMs (Tata Motors, Honda, Maruti Suzuki)",
                "manufacturer_id", "name",
                List.of(
                        new FieldDef("name", "Manufacturer Name", "text", true, "e.g. Tata Motors Ltd", "Registered OEM brand name")
                )
        ));

        CONFIGS.put("vehicle-models", new RefConfig(
                "vehicle-models", "/vehicle-models", "Vehicle Models", "Commercial variants and models grouped by manufacturer",
                "model_id", "model_name",
                List.of(
                        new FieldDef("manufacturer_id", "Manufacturer", "select_manufacturer", true, null, "Parent OEM manufacturer"),
                        new FieldDef("model_name", "Model Name", "text", true, "e.g. Nexon EV", "Specific vehicle model and variant")
                )
        ));
    }

    public AdminReferenceController(ApiClient api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @GetMapping
    public String index(Model model) {
        requireManage();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map.Entry<String, RefConfig> e : CONFIGS.entrySet()) {
            try {
                JsonNode res = api.get(e.getValue().endpoint(), Map.of("page", 1, "page_size", 1));
                counts.put(e.getKey(), res.path("total").asLong(0));
            } catch (Exception ex) {
                counts.put(e.getKey(), 0L);
            }
        }
        model.addAttribute("configs", CONFIGS.values());
        model.addAttribute("counts", counts);
        return "admin/reference/index";
    }

    @GetMapping("/{type}")
    public String list(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String search,
            Model model) {
        requireManage();
        RefConfig cfg = getConfig(type);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("page_size", 15);
        if (search != null && !search.isBlank()) {
            query.put("search", search.trim());
        }

        JsonNode result = api.get(cfg.endpoint(), query);

        model.addAttribute("cfg", cfg);
        model.addAttribute("result", result);
        model.addAttribute("search", search);
        model.addAttribute("pagerBase", Paging.base("/admin/reference/" + type, search != null && !search.isBlank() ? Map.of("search", search.trim()) : Map.of()));

        // Preload options for select fields if needed
        populateSelectOptions(cfg, model);

        return "admin/reference/list";
    }

    @PostMapping("/{type}")
    public String create(
            @PathVariable String type,
            @RequestParam Map<String, String> params,
            RedirectAttributes ra) {
        requireManage();
        RefConfig cfg = getConfig(type);

        Map<String, Object> body = prepareBody(cfg, params, false);

        try {
            api.post(cfg.endpoint(), body);
            ra.addFlashAttribute("flash", "Added new record to " + cfg.title() + ".");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to create: " + e.getMessage() + formatErrors(e));
        }

        return "redirect:/admin/reference/" + type;
    }

    @PostMapping("/{type}/{id}/edit")
    public String update(
            @PathVariable String type,
            @PathVariable long id,
            @RequestParam Map<String, String> params,
            RedirectAttributes ra) {
        requireManage();
        RefConfig cfg = getConfig(type);

        Map<String, Object> body = prepareBody(cfg, params, true);

        try {
            api.patch(cfg.endpoint() + "/" + id, body);
            ra.addFlashAttribute("flash", "Updated record in " + cfg.title() + ".");
        } catch (ApiException e) {
            ra.addFlashAttribute("flashError", "Failed to update: " + e.getMessage() + formatErrors(e));
        }

        return "redirect:/admin/reference/" + type;
    }

    @PostMapping("/{type}/{id}/delete")
    public String delete(
            @PathVariable String type,
            @PathVariable long id,
            RedirectAttributes ra) {
        requireManage();
        RefConfig cfg = getConfig(type);

        try {
            api.delete(cfg.endpoint() + "/" + id);
            ra.addFlashAttribute("flash", "Record deleted from " + cfg.title() + ".");
        } catch (ApiException e) {
            if (e.status() == 409) {
                ra.addFlashAttribute("flashError", "Cannot delete: this record is referenced by other active entities in the system.");
            } else {
                ra.addFlashAttribute("flashError", "Failed to delete: " + e.getMessage());
            }
        }

        return "redirect:/admin/reference/" + type;
    }

    private void requireManage() {
        if (!session.can("reference.manage")) {
            throw new ApiException(403, "FORBIDDEN", "You do not have permission to manage reference data (requires reference.manage).", null);
        }
    }

    private RefConfig getConfig(String type) {
        RefConfig cfg = CONFIGS.get(type);
        if (cfg == null) {
            throw new ApiException(404, "NOT_FOUND", "Unknown reference data type: " + type, null);
        }
        return cfg;
    }

    private Map<String, Object> prepareBody(RefConfig cfg, Map<String, String> params, boolean isPatch) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (FieldDef f : cfg.fields()) {
            String raw = params.get(f.name());
            if ("boolean".equals(f.type())) {
                body.put(f.name(), "true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "1".equals(raw));
            } else if ("number".equals(f.type()) || "select_parent_region".equals(f.type()) || "select_manufacturer".equals(f.type())) {
                if (raw != null && !raw.isBlank()) {
                    try {
                        body.put(f.name(), Long.parseLong(raw.trim()));
                    } catch (NumberFormatException ignored) {}
                } else if (isPatch) {
                    body.put(f.name(), null);
                }
            } else if ("decimal".equals(f.type())) {
                if (raw != null && !raw.isBlank()) {
                    body.put(f.name(), raw.trim());
                } else if (isPatch) {
                    body.put(f.name(), null);
                }
            } else {
                if (raw != null && !raw.isBlank()) {
                    body.put(f.name(), raw.trim());
                } else if (isPatch) {
                    body.put(f.name(), null);
                }
            }
        }
        return body;
    }

    private void populateSelectOptions(RefConfig cfg, Model model) {
        for (FieldDef f : cfg.fields()) {
            if ("select_parent_region".equals(f.type())) {
                try {
                    JsonNode regions = api.get("/regions", Map.of("page", 1, "page_size", 100));
                    model.addAttribute("parentRegionOptions", regions.path("items"));
                } catch (Exception ignored) {}
            } else if ("select_manufacturer".equals(f.type())) {
                try {
                    JsonNode mfrs = api.get("/vehicle-manufacturers", Map.of("page", 1, "page_size", 100));
                    model.addAttribute("manufacturerOptions", mfrs.path("items"));
                } catch (Exception ignored) {}
            }
        }
    }

    private static String formatErrors(ApiException e) {
        Map<String, String> fe = Forms.fieldErrors(e.errors());
        if (fe.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" (");
        fe.forEach((k, v) -> sb.append(k).append(": ").append(v).append("; "));
        sb.append(")");
        return sb.toString();
    }
}
