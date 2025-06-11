package org.thisway.vehicle.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.vehicle.dto.request.VehicleModelCreateRequest;

@RestController
@RequestMapping("/api/vehicle-models")
@RequiredArgsConstructor
public class VehicleModelController {

  @PostMapping
  public void registerVehicleModel(@RequestBody @Validated VehicleModelCreateRequest request) {


  }

}
