package com.quickcare.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class MaiorDeIdadeValidator implements ConstraintValidator<MaiorDeIdade, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return Maioridade.atingida(value, LocalDate.now(context.getClockProvider().getClock()));
    }
}
