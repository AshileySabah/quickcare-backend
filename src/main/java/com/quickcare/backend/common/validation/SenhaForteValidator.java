package com.quickcare.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    private static final Pattern MAIUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern ESPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        if (senha == null || senha.length() < 8) {
            return false;
        }
        return MAIUSCULA.matcher(senha).find()
                && MINUSCULA.matcher(senha).find()
                && ESPECIAL.matcher(senha).find();
    }
}