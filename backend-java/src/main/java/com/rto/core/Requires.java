package com.rto.core;

import java.lang.annotation.*;

/** The caller must hold ALL listed permissions (resolved from user_roles + role_permissions on every request). */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Requires {
    String[] value();
}
