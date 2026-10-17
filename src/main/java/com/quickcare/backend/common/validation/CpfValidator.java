package com.quickcare.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<Cpf, String> {

    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        return cpf == null || DocumentosBrasileiros.cpfValido(cpf);
    }
}
