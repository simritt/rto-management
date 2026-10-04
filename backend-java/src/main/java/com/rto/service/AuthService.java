package com.rto.service;

import com.rto.core.ApiException;
import com.rto.core.Clock;
import com.rto.core.CurrentUser;
import com.rto.core.Db;
import com.rto.core.Settings;
import com.rto.domain.Person;
import com.rto.domain.User;
import com.rto.dto.AuthDto.MeResponse;
import com.rto.dto.AuthDto.PersonBrief;
import com.rto.dto.AuthDto.TokenResponse;
import com.rto.security.JwtService;
import com.rto.security.Passwords;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final Db db;
    private final JwtService jwt;
    private final Passwords passwords;
    private final Settings settings;
    /** Verified against when the username does not exist, so response time doesn't reveal valid usernames. */
    private volatile String dummyHash;

    public AuthService(Db db, JwtService jwt, Passwords passwords, Settings settings) {
        this.db = db;
        this.jwt = jwt;
        this.passwords = passwords;
        this.settings = settings;
    }

    private TokenResponse tokens(long userId) {
        return new TokenResponse(jwt.accessToken(userId), jwt.refreshToken(userId), "bearer",
                settings.accessTokenMinutes() * 60L);
    }

    @Transactional
    public TokenResponse login(String username, String password) {
        User user = db.first(User.class, "select u from User u where u.username = :n", "n", username);
        if (dummyHash == null) dummyHash = passwords.hash("dummy-password-for-timing");
        boolean ok = passwords.verify(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        if (!Boolean.TRUE.equals(user.getIsActive())) throw ApiException.forbidden("USER_INACTIVE", "This account is inactive");
        user.setLastLoginAt(Clock.now());
        return tokens(user.getUserId());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Claims c = jwt.decode(refreshToken, "refresh");
        User user = db.find(User.class, Long.parseLong(c.getSubject()));
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.unauthorized("USER_INACTIVE", "User is inactive or no longer exists");
        }
        jwt.revoke(c);   // refresh-token rotation: each token is single-use
        return tokens(user.getUserId());
    }

    public void logout(Claims accessClaims, String refreshToken) {
        jwt.revoke(accessClaims);
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                jwt.revoke(jwt.decode(refreshToken, "refresh"));
            } catch (ApiException ignored) {
                // already expired/invalid: nothing to revoke
            }
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser u) {
        Person p = db.get(Person.class, u.personId(), "Person");
        return new MeResponse(u.userId(), u.username(),
                new PersonBrief(p.getPersonId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.getPhonePrimary()),
                u.roles(), u.permissions().stream().sorted().toList(), u.employeeId(), u.citizenId(),
                List.copyOf(u.officeIds()));
    }
}
