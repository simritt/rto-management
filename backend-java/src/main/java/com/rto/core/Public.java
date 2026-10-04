package com.rto.core;

import java.lang.annotation.*;

/** Endpoint needs no authentication (health, login, refresh). Every other API endpoint requires a valid access token. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Public {
}
