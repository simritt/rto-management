package com.rto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDto {
    private AuthDto() {}

    public record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 128) String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

    public record PersonBrief(Long personId, String firstName, String lastName, String email, String phonePrimary) {}

    public record MeResponse(Long userId, String username, PersonBrief person, List<String> roles,
                             List<String> permissions, Long employeeId, Long citizenId, List<Long> officeIds) {}
}
