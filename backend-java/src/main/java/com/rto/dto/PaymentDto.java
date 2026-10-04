package com.rto.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PaymentDto {
    private PaymentDto() {}

    public static final String PAYABLE = "^(APPLICATION|CHALLAN|PERMIT|ROAD_TAX)$";
    public static final String OUTCOME = "^(SUCCESS|FAILED|TIMEOUT)$";
    public static final String REF = "^[A-Za-z0-9._:/-]+$";

    public record PaymentCreate(@NotNull @Pattern(regexp = PAYABLE) String payableType, @NotNull Long payableId,
                                @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                @Size(min = 3, max = 60) @Pattern(regexp = REF) String gatewayReference,
                                @Pattern(regexp = OUTCOME) String outcome) {}

    public record AttemptIn(@NotNull @Size(min = 3, max = 60) @Pattern(regexp = REF) String gatewayReference,
                            @NotNull @Pattern(regexp = OUTCOME) String outcome) {}

    public record PaymentOut(Long paymentId, String receiptNumber, String payableType, Long payableId, BigDecimal amount, String status,
                             LocalDateTime paidAt, LocalDateTime createdAt, BigDecimal refundedAmount, BigDecimal refundableAmount,
                             Long attempts, Boolean idempotentReplay) {}

    public record RefundCreate(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal amount,
                               @NotNull @Size(min = 3, max = 255) String reason, Boolean autoComplete) {
        public RefundCreate {
            autoComplete = autoComplete == null ? Boolean.TRUE : autoComplete;
        }
    }

    public record RefundFail(@Size(max = 255) String reason) {}

    public record FeeCreate(@NotNull Long serviceTypeId, @NotBlank @Size(max = 80) String componentName,
                            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                            @NotNull LocalDate effectiveFrom, LocalDate effectiveTo) {}

    public record FeeUpdate(@Size(min = 1, max = 80) String componentName, @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                            LocalDate effectiveTo) {}
}
