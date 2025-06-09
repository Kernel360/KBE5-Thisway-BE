package org.thisway.vehicle;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CarNumberValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp(){
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("차량번호 형식 검증")
    void 차량번호검증_통과(){
        //given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", "12가3456", "흰색");

        // when
        Set<ConstraintViolation<VehicleCreateRequest>> violations = validator.validate(request);

        // then
        assertTrue(violations.isEmpty());
    }

    @ParameterizedTest
    @DisplayName("다양한 유효한 차량번호 형식 검증")
    @ValueSource(strings = {
            "12가3456",
            "123가4567",
            "아1234",
            "바12가3456"
    })
    void 다양한_유효한_차량번호_검증(String carNumber) {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", carNumber, "흰색");

        // when
        Set<ConstraintViolation<VehicleCreateRequest>> violations = validator.validate(request);

        // then
        assertTrue(violations.isEmpty(), "차량번호 " + carNumber + "는 유효해야 합니다");
    }

    @ParameterizedTest
    @DisplayName("잘못된 차량번호 형식 검증")
    @ValueSource(strings = {
            "가나다라",
            "1234567",
            "12345678",
            "12가345",
            "12!3456",
            "가나다1234",
            "1가23456",
            "1234가12"
    })
    void 잘못된_차량번호_검증(String carNumber) {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", carNumber, "흰색");

        // when
        Set<ConstraintViolation<VehicleCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size(), "차량번호 " + carNumber + "는 유효하지 않아야 합니다");

        ConstraintViolation<VehicleCreateRequest> violation = violations.iterator().next();
        assertEquals("carNumber", violation.getPropertyPath().toString());
        assertEquals("유효하지 않은 차량 번호입니다", violation.getMessage());
    }
}
