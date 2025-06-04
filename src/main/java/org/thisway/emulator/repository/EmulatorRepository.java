package org.thisway.emulator.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.emulator.entity.Emulator;
import org.thisway.vehicle.api.EmulatorFinder;

public interface EmulatorRepository extends JpaRepository<Emulator, Long>, EmulatorFinder {
    @Override
    Optional<Emulator> findByMdn(String mdn);

    @Override
    Optional<Emulator> findByVehicleId(Long vehicleId);
}
