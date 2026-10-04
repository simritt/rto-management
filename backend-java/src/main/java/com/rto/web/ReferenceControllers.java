package com.rto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ReferenceDto.*;
import com.rto.service.ReferenceService;
import com.rto.service.ReferenceService.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reference / master-data APIs. Reads need authentication only; writes need `reference.manage`.
 * Every resource shares the same five operations (list, get, create, patch, delete) via {@link Base}.
 */
public final class ReferenceControllers {
    private ReferenceControllers() {}

    private static Resource res(Class<?> type, String label, String pk, List<String> search, List<List<String>> unique,
                                Map<String, String> sort, Set<String> nullable, Map<String, String> filters,
                                ReferenceService.Hook hook) {
        return new Resource(type, label, pk, search, unique, sort, nullable, filters, hook);
    }

    public abstract static class Base<E, C> {
        private final ReferenceService svc;
        private final Resource res;
        private final Class<E> type;
        private final Class<?> updateDto;

        protected Base(ReferenceService svc, Resource res, Class<E> type, Class<?> updateDto) {
            this.svc = svc;
            this.res = res;
            this.type = type;
            this.updateDto = updateDto;
        }

        @GetMapping
        public PageResponse<E> list(PageParams p, HttpServletRequest req) {
            Map<String, Object> filters = new LinkedHashMap<>();
            res.filters().keySet().forEach(param -> {
                String v = req.getParameter(param);
                if (v != null && !v.isBlank()) {
                    try {
                        filters.put(param, Long.parseLong(v));
                    } catch (NumberFormatException e) {
                        throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", param, "message", "must be an integer")));
                    }
                }
            });
            return svc.list(res, type, p, filters);
        }

        @GetMapping("/{itemId}")
        public E get(@PathVariable Long itemId) {
            return svc.get(res, type, itemId);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Requires("reference.manage")
        public E create(@Valid @RequestBody C body, CurrentUser user) {
            return svc.create(res, type, body, user);
        }

        @PatchMapping("/{itemId}")
        @Requires("reference.manage")
        public E update(@PathVariable Long itemId, @RequestBody JsonNode body, CurrentUser user) {
            return svc.update(res, type, itemId, body, updateDto, user);
        }

        @DeleteMapping("/{itemId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Requires("reference.manage")
        @Operation(summary = "Delete (409 if the record is referenced elsewhere)")
        public void delete(@PathVariable Long itemId, CurrentUser user) {
            svc.delete(res, itemId, user);
        }
    }

    @RestController
    @RequestMapping("/api/v1/regions")
    @Tag(name = "reference data")
    public static class Regions extends Base<Region, RegionCreate> {
        private final ReferenceService service;

        public Regions(ReferenceService s) {
            super(s, res(Region.class, "Region", "regionId", List.of("regionName", "regionCode"), List.of(List.of("regionCode")),
                    Map.of("region_name", "regionName", "region_code", "regionCode", "region_id", "regionId"),
                    Set.of("parent_region_id"), Map.of(), ReferenceService.REGION_HOOK), Region.class, RegionUpdate.class);
            this.service = s;
        }

        @GetMapping("/tree")
        @Operation(summary = "Region hierarchy as a nested tree (any depth)")
        public List<RegionNode> tree(@RequestParam(name = "root_id", required = false) Long rootId) {
            return service.regionTree(rootId);
        }
    }

    @RestController
    @RequestMapping("/api/v1/vehicle-manufacturers")
    @Tag(name = "reference data")
    public static class Manufacturers extends Base<VehicleManufacturer, ManufacturerIn> {
        public Manufacturers(ReferenceService s) {
            super(s, res(VehicleManufacturer.class, "Manufacturer", "manufacturerId", List.of("name"), List.of(List.of("name")),
                    Map.of("name", "name", "manufacturer_id", "manufacturerId"), Set.of(), Map.of(), null),
                    VehicleManufacturer.class, ManufacturerIn.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/vehicle-models")
    @Tag(name = "reference data")
    public static class Models extends Base<VehicleModel, ModelCreate> {
        public Models(ReferenceService s) {
            super(s, res(VehicleModel.class, "Vehicle model", "modelId", List.of("modelName"),
                    List.of(List.of("manufacturerId", "modelName")), Map.of("model_name", "modelName", "model_id", "modelId"),
                    Set.of(), Map.of("manufacturer_id", "manufacturerId"), ReferenceService.MODEL_HOOK),
                    VehicleModel.class, ModelUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/vehicle-types")
    @Tag(name = "reference data")
    public static class VehicleTypes extends Base<VehicleType, VehicleTypeIn> {
        public VehicleTypes(ReferenceService s) {
            super(s, res(VehicleType.class, "Vehicle type", "vehicleTypeId", List.of("typeName"), List.of(List.of("typeName")),
                    Map.of("type_name", "typeName", "vehicle_type_id", "vehicleTypeId"), Set.of(), Map.of(), null),
                    VehicleType.class, VehicleTypeUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/fuel-types")
    @Tag(name = "reference data")
    public static class FuelTypes extends Base<FuelType, FuelTypeIn> {
        public FuelTypes(ReferenceService s) {
            super(s, res(FuelType.class, "Fuel type", "fuelTypeId", List.of("fuelName"), List.of(List.of("fuelName")),
                    Map.of("fuel_name", "fuelName", "fuel_type_id", "fuelTypeId"), Set.of(), Map.of(), null),
                    FuelType.class, FuelTypeIn.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/licence-classes")
    @Tag(name = "reference data")
    public static class LicenceClasses extends Base<LicenceClass, LicenceClassIn> {
        public LicenceClasses(ReferenceService s) {
            super(s, res(LicenceClass.class, "Licence class", "licenceClassId", List.of("classCode", "description"),
                    List.of(List.of("classCode")), Map.of("class_code", "classCode", "licence_class_id", "licenceClassId"),
                    Set.of(), Map.of(), null), LicenceClass.class, LicenceClassUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/document-types")
    @Tag(name = "reference data")
    public static class DocumentTypes extends Base<DocumentType, DocumentTypeIn> {
        public DocumentTypes(ReferenceService s) {
            super(s, res(DocumentType.class, "Document type", "documentTypeId", List.of("typeName"), List.of(List.of("typeName")),
                    Map.of("type_name", "typeName", "document_type_id", "documentTypeId"), Set.of("validity_period_days"),
                    Map.of(), null), DocumentType.class, DocumentTypeUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/service-types")
    @Tag(name = "reference data")
    public static class ServiceTypes extends Base<ServiceType, ServiceTypeIn> {
        public ServiceTypes(ReferenceService s) {
            super(s, res(ServiceType.class, "Service type", "serviceTypeId", List.of("serviceName", "serviceCode"),
                    List.of(List.of("serviceCode")), Map.of("service_name", "serviceName", "service_code", "serviceCode",
                            "base_fee", "baseFee", "service_type_id", "serviceTypeId"), Set.of(), Map.of(), null),
                    ServiceType.class, ServiceTypeUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/violation-types")
    @Tag(name = "reference data")
    public static class ViolationTypes extends Base<ViolationType, ViolationTypeIn> {
        public ViolationTypes(ReferenceService s) {
            super(s, res(ViolationType.class, "Violation type", "violationTypeId", List.of("description"),
                    List.of(List.of("description")), Map.of("description", "description", "base_fine_amount", "baseFineAmount",
                            "violation_type_id", "violationTypeId"), Set.of(), Map.of(), null),
                    ViolationType.class, ViolationTypeUpdate.class);
        }
    }

    @RestController
    @RequestMapping("/api/v1/permit-types")
    @Tag(name = "reference data")
    public static class PermitTypes extends Base<PermitType, PermitTypeIn> {
        public PermitTypes(ReferenceService s) {
            super(s, res(PermitType.class, "Permit type", "permitTypeId", List.of("typeName"), List.of(List.of("typeName")),
                    Map.of("type_name", "typeName", "permit_type_id", "permitTypeId"), Set.of(), Map.of(), null),
                    PermitType.class, PermitTypeUpdate.class);
        }
    }

    /** payable_types is read-only on purpose: each name is wired to a target resolver in the payment service. */
    @RestController
    @RequestMapping("/api/v1/payable-types")
    @Tag(name = "reference data")
    public static class PayableTypes {
        private final Db db;

        public PayableTypes(Db db) {
            this.db = db;
        }

        @GetMapping
        @Operation(summary = "Payment target types (APPLICATION, CHALLAN, PERMIT, ROAD_TAX)")
        public List<PayableType> list() {
            return db.list(PayableType.class, "select p from PayableType p order by p.payableTypeId");
        }
    }
}
