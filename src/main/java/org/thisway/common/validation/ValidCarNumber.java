package org.thisway.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Constraint(validatedBy = CarNumberValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCarNumber {
    String message() default "유효하지 않은 차량 번호입니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
