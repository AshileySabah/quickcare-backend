package com.quickcare.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    @Override
    public boolean isValid(String cnpj, ConstraintValidatorContext context) {
        return cnpj == null || cnpj.isBlank() || DocumentosBrasileiros.cnpjValido(cnpj);
    }
}
