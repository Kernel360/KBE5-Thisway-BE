package org.thisway.emulator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.vehicle.entity.Vehicle;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Emulator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String mdn;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String terminalId;

    @Column(nullable = false)
    private Integer manufactureId;

    @Column(nullable = false)
    private Integer packetVersion;

    @Column(nullable = false)
    private Integer deviceId;

    @Column(nullable = false)
    private String deviceFirmwareVersion;

    @Builder
    public Emulator(
            Long id,
            String mdn,
            Vehicle vehicle,
            String terminalId,
            Integer manufactureId,
            Integer packetVersion,
            Integer deviceId,
            String deviceFirmwareVersion
    ) {
        this.id = id;
        this.mdn = mdn;
        this.vehicle = vehicle;
        this.terminalId = terminalId;
        this.manufactureId = manufactureId;
        this.packetVersion = packetVersion;
        this.deviceId = deviceId;
        this.deviceFirmwareVersion = deviceFirmwareVersion;
    }

    public void update(
            String mdn,
            Vehicle vehicle,
            String terminalId,
            Integer manufactureId,
            Integer packetVersion,
            Integer deviceId,
            String deviceFirmwareVersion
    ) {
        if (mdn != null) {
            this.mdn = mdn;
        }
        if (vehicle != null) {
            this.vehicle = vehicle;
        }
        if (terminalId != null) {
            this.terminalId = terminalId;
        }
        if (manufactureId != null) {
            this.manufactureId = manufactureId;
        }
        if (packetVersion != null) {
            this.packetVersion = packetVersion;
        }
        if (deviceId != null) {
            this.deviceId = deviceId;
        }
        if (deviceFirmwareVersion != null) {
            this.deviceFirmwareVersion = deviceFirmwareVersion;
        }
    }
}
