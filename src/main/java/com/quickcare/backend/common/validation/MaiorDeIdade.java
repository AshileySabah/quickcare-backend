package com.quickcare.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaiorDeIdadeValidator.class)
public @interface MaiorDeIdade {
    String message() default "É preciso ter pelo menos 18 anos para usar a plataforma.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
