package org.thisway.vehicle.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class CarNumberValidator implements ConstraintValidator<ValidCarNumber, String> {

    private static final Pattern CAR_NUMBER_PATTERN = Pattern.compile(
            "^\\d{2,3}[가-힣]{1}\\s?\\d{4}$|" +
                    "^[가-힣]{2}\\s?[가-힣]{1}\\s?\\d{4}$|" +
                    "^[가-힣]{1}\\s?\\d{4}$|" +
                    "^[가-힣]{1}\\s?\\d{2}[가-힣]{1}\\s?\\d{4}$"
    );

    private static final Pattern INVALID_PATTERN = Pattern.compile("[가-힣]{3,}");

    @Override
    public boolean isValid(String carNumber, ConstraintValidatorContext context) {

        String normalizedCarNumber = carNumber.replace(" ", "");

        if (INVALID_PATTERN.matcher(normalizedCarNumber).find()) {
            return false;
        }

        return CAR_NUMBER_PATTERN.matcher(normalizedCarNumber).matches();
    }
}
