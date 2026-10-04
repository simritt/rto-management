package com.rto.dto;

import com.rto.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class IdentityDto {
    private IdentityDto() {}

    public static final String PHONE = "^\\+?[0-9]{7,14}$";
    public static final String NATIONAL_ID = "^[A-Za-z0-9-]{4,20}$";
    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String GENDER = "^(MALE|FEMALE|OTHER)$";

    public record AddressCreate(@NotBlank @Size(max = 150) String line1, @Size(max = 150) String line2,
                                @NotBlank @Size(max = 80) String city, @NotBlank @Size(max = 80) String state,
                                @NotNull @Pattern(regexp = "^[0-9A-Za-z -]{3,10}$") String pincode,
                                @Pattern(regexp = "^(PERMANENT|CURRENT|OFFICE)$") String addressType,
                                LocalDate validFrom, LocalDate validTo) {}

    /** Shared person block used when a citizen, employee or instructor is created together with their person row. */
    public record PersonFields(@NotBlank @Size(max = 60) String firstName, @NotBlank @Size(max = 60) String lastName,
                               @NotNull @PastOrPresent LocalDate dateOfBirth,
                               @NotNull @Pattern(regexp = GENDER) String gender,
                               @NotNull @Pattern(regexp = NATIONAL_ID) String nationalIdNumber,
                               @NotNull @Pattern(regexp = PHONE) String phonePrimary,
                               @Pattern(regexp = PHONE) String phoneSecondary,
                               @Size(max = 120) @Pattern(regexp = EMAIL) String email) {}

    public record CitizenCreate(@NotBlank @Size(max = 60) String firstName, @NotBlank @Size(max = 60) String lastName,
                                @NotNull @PastOrPresent LocalDate dateOfBirth,
                                @NotNull @Pattern(regexp = GENDER) String gender,
                                @NotNull @Pattern(regexp = NATIONAL_ID) String nationalIdNumber,
                                @NotNull @Pattern(regexp = PHONE) String phonePrimary,
                                @Pattern(regexp = PHONE) String phoneSecondary,
                                @Size(max = 120) @Pattern(regexp = EMAIL) String email,
                                @Size(min = 3, max = 20) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String citizenCode,
                                @Valid AddressCreate address) {}

    public record CitizenUpdate(@Size(min = 1, max = 60) String firstName, @Size(min = 1, max = 60) String lastName,
                                @PastOrPresent LocalDate dateOfBirth, @Pattern(regexp = GENDER) String gender,
                                @Pattern(regexp = NATIONAL_ID) String nationalIdNumber,
                                @Pattern(regexp = PHONE) String phonePrimary, @Pattern(regexp = PHONE) String phoneSecondary,
                                @Size(max = 120) @Pattern(regexp = EMAIL) String email, Boolean blacklisted,
                                @Size(max = 255) String blacklistReason) {}

    /** national_id_number is only populated for callers holding citizen.view_sensitive, never in lists. */
    public record CitizenOut(Long citizenId, Long personId, String citizenCode, String firstName, String lastName,
                             LocalDate dateOfBirth, String gender, String phonePrimary, String phoneSecondary,
                             String email, Boolean blacklisted, String blacklistReason, LocalDateTime registeredAt,
                             String nationalIdNumber, Address currentAddress) {}

    public record CitizenListItem(Long citizenId, String citizenCode, String firstName, String lastName,
                                  String phonePrimary, Boolean blacklisted, LocalDateTime registeredAt) {}
}
