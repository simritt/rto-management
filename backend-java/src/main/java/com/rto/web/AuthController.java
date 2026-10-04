package com.rto.web;

import com.rto.core.CurrentUser;
import com.rto.core.Public;
import com.rto.dto.AuthDto.*;
import com.rto.service.AuthService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Public
    @PostMapping("/login")
    @Operation(summary = "Exchange username/password for JWTs")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body.username(), body.password());
    }

    @Public
    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new token pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current access token (and optionally a refresh token)")
    public void logout(@RequestBody(required = false) LogoutRequest body, HttpServletRequest req, CurrentUser user) {
        auth.logout((Claims) req.getAttribute("rto.claims"), body == null ? null : body.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Authenticated user, roles and resolved permissions")
    public MeResponse me(CurrentUser user) {
        return auth.me(user);
    }
}
