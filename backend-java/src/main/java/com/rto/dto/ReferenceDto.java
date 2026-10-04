package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ReferenceDto {
    private ReferenceDto() {}

    private static final String CODE = "^[A-Za-z0-9_-]+$";

    public record RegionCreate(@NotBlank @Size(max = 100) String regionName,
                               @NotNull @Size(min = 1, max = 10) @Pattern(regexp = CODE) String regionCode, Long parentRegionId) {}

    public record RegionUpdate(@Size(min = 1, max = 100) String regionName,
                               @Size(min = 1, max = 10) @Pattern(regexp = CODE) String regionCode, Long parentRegionId) {}

    public record RegionNode(Long regionId, String regionName, String regionCode, Long parentRegionId, List<RegionNode> children) {
        public RegionNode(Long regionId, String regionName, String regionCode, Long parentRegionId) {
            this(regionId, regionName, regionCode, parentRegionId, new ArrayList<>());
        }
    }

    public record ManufacturerIn(@NotBlank @Size(max = 100) String name) {}

    public record ModelCreate(@NotNull Long manufacturerId, @NotBlank @Size(max = 100) String modelName) {}

    public record ModelUpdate(Long manufacturerId, @Size(min = 1, max = 100) String modelName) {}

    public record VehicleTypeIn(@NotBlank @Size(max = 50) String typeName, Boolean isCommercial) {
        public VehicleTypeIn {
            isCommercial = isCommercial == null ? Boolean.FALSE : isCommercial;
        }
    }

    public record VehicleTypeUpdate(@Size(min = 1, max = 50) String typeName, Boolean isCommercial) {}

    public record FuelTypeIn(@NotBlank @Size(max = 30) String fuelName) {}

    public record LicenceClassIn(@NotNull @Size(min = 1, max = 10) @Pattern(regexp = CODE) String classCode,
                                 @NotBlank @Size(max = 150) String description) {}

    public record LicenceClassUpdate(@Size(min = 1, max = 10) @Pattern(regexp = CODE) String classCode,
                                     @Size(min = 1, max = 150) String description) {}

    public record DocumentTypeIn(@NotBlank @Size(max = 100) String typeName, Boolean isMandatoryDefault,
                                 @Min(1) @Max(36500) Integer validityPeriodDays) {
        public DocumentTypeIn {
            isMandatoryDefault = isMandatoryDefault == null ? Boolean.TRUE : isMandatoryDefault;
        }
    }

    public record DocumentTypeUpdate(@Size(min = 1, max = 100) String typeName, Boolean isMandatoryDefault,
                                     @Min(1) @Max(36500) Integer validityPeriodDays) {}

    public record ServiceTypeIn(@NotBlank @Size(max = 100) String serviceName,
                                @NotNull @Size(min = 1, max = 30) @Pattern(regexp = CODE) String serviceCode,
                                @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal baseFee,
                                @Min(1) @Max(3650) Integer slaDays) {
        public ServiceTypeIn {
            baseFee = baseFee == null ? new BigDecimal("0.00") : baseFee;
            slaDays = slaDays == null ? 7 : slaDays;
        }
    }

    public record ServiceTypeUpdate(@Size(min = 1, max = 100) String serviceName,
                                    @Size(min = 1, max = 30) @Pattern(regexp = CODE) String serviceCode,
                                    @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal baseFee,
                                    @Min(1) @Max(3650) Integer slaDays) {}

    public record ViolationTypeIn(@NotBlank @Size(max = 150) String description,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal baseFineAmount,
                                  Boolean isCognizable) {
        public ViolationTypeIn {
            isCognizable = isCognizable == null ? Boolean.FALSE : isCognizable;
        }
    }

    public record ViolationTypeUpdate(@Size(min = 1, max = 150) String description,
                                      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal baseFineAmount,
                                      Boolean isCognizable) {}

    public record PermitTypeIn(@NotBlank @Size(max = 50) String typeName, @NotNull @Min(1) @Max(1200) Integer validityMonths) {}

    public record PermitTypeUpdate(@Size(min = 1, max = 50) String typeName, @Min(1) @Max(1200) Integer validityMonths) {}
}
