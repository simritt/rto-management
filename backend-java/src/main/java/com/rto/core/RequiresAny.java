package com.rto.core;

import java.lang.annotation.*;

/** The caller must hold AT LEAST ONE of the listed permissions. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAny {
    String[] value();
}
