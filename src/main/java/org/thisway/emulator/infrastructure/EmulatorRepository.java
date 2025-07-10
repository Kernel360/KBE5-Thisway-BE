package org.thisway.emulator.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thisway.emulator.domain.Emulator;
import org.thisway.vehicle.domain.VehicleReference;

import java.util.Optional;

public interface EmulatorRepository extends JpaRepository<Emulator, Long> {
    Optional<Emulator> findByMdn(String mdn);

    Optional<Emulator> findByVehicleId(Long vehicleId);

    @Query(""" 
                SELECT new org.thisway.vehicle.domain.VehicleReference(e.vehicle.id, e.vehicle.company.id)
                FROM Emulator e
                WHERE e.mdn = :mdn
            """)
    Optional<VehicleReference> findVehicleByMdn(@Param("mdn") String mdn);
}
