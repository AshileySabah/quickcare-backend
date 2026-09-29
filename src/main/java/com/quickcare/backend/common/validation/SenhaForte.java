package com.quickcare.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaForteValidator.class)
public @interface SenhaForte {
    String message() default "Senha deve ter no mínimo 8 caracteres, com maiúscula, minúscula e caractere especial";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}