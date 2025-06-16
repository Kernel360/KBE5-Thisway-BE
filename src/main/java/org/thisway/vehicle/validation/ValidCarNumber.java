package org.thisway.vehicle.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Constraint(validatedBy = CarNumberValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCarNumber {
    String message() default "14006";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
