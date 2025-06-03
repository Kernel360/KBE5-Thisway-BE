package org.thisway.emulator.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.emulator.entity.Emulator;

public interface EmulatorRepository extends JpaRepository<Emulator, Long> {
    Optional<Emulator> findByMdn(String mdn);

    Optional<Emulator> findByVehicleId(Long vehicleId);
}
