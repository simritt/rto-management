package com.rto.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** bcrypt (cost 12). Hashes are interchangeable with the Python reference backend ($2a$/$2b$). */
@Component
public class Passwords {
    public static final int MAX_BYTES = 72;   // bcrypt limit: longer input is rejected, never silently truncated
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String plain) {
        if (plain.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Password must be at most 72 bytes");
        }
        return encoder.encode(plain);
    }

    public boolean verify(String plain, String hash) {
        try {
            return plain.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES && encoder.matches(plain, hash);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
