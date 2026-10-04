package com.rto.core;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

/** At least 8 characters and at most 72 bytes (the bcrypt limit); null passes so optional fields can use it. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {
    String message() default "Password must be at least 8 characters and at most 72 bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {
        @Override
        public boolean isValid(String v, ConstraintValidatorContext c) {
            return v == null || (v.length() >= 8 && v.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }
}
