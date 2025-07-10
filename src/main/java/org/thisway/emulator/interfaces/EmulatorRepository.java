package org.thisway.emulator.interfaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thisway.emulator.domain.Emulator;
import org.thisway.vehicle.dto.VehicleReference;

import java.util.Optional;

public interface EmulatorRepository extends JpaRepository<Emulator, Long> {
    Optional<Emulator> findByMdn(String mdn);

    Optional<Emulator> findByVehicleId(Long vehicleId);

    @Query(""" 
                SELECT new org.thisway.vehicle.dto.VehicleReference(e.vehicle.id, e.vehicle.company.id)
                FROM Emulator e
                WHERE e.mdn = :mdn
            """)
    Optional<VehicleReference> findVehicleByMdn(@Param("mdn") String mdn);
}
