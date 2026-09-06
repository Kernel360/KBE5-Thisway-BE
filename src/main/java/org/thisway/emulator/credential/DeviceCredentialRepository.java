package org.thisway.emulator.credential;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeviceCredentialRepository {
    private final JdbcTemplate jdbc;

    public record Binding(long emulatorId, long vehicleId, long companyId, String mdn,
                          long assignmentRevision, boolean active) {}
    public record Snapshot(boolean hasKey, long vehicleId, long companyId, String mdn,
                           Instant issuedAt, Instant expiresAt, Instant revokedAt, long assignmentRevision) {}

    public Optional<Binding> findOwned(long id, long companyId, boolean lock) {
        String sql = """
                SELECT e.id,e.vehicle_id,e.mdn,e.assignment_revision,v.active FROM emulator e JOIN vehicle v ON v.id=e.vehicle_id
                JOIN company c ON c.id=v.company_id
                WHERE e.id=? AND v.company_id=? AND c.active=true
                """ + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> new Binding(rs.getLong("id"), rs.getLong("vehicle_id"),
                companyId, rs.getString("mdn"), rs.getLong("assignment_revision"), rs.getBoolean("active")), id, companyId).stream().findFirst();
    }

    public void replace(Binding binding, String hash, Instant issued, Instant expires) {
        // Caller locks the emulator. Avoid an upsert that could target another row on hash collision.
        int updated = jdbc.update("""
                UPDATE device_credential SET key_hash=?,bound_vehicle_id=?,bound_company_id=?,bound_mdn=?,
                issued_at=?,expires_at=?,revoked_at=NULL,bound_assignment_revision=? WHERE emulator_id=?
                """, hash, binding.vehicleId(), binding.companyId(), binding.mdn(), utc(issued), utc(expires), binding.assignmentRevision(), binding.emulatorId());
        if (updated == 0) jdbc.update("""
                INSERT INTO device_credential(emulator_id,key_hash,bound_vehicle_id,bound_company_id,bound_mdn,issued_at,expires_at,bound_assignment_revision)
                VALUES(?,?,?,?,?,?,?,?)
                """, binding.emulatorId(), hash, binding.vehicleId(), binding.companyId(), binding.mdn(), utc(issued), utc(expires), binding.assignmentRevision());
    }

    public boolean revoke(long id, Instant time) {
        return jdbc.update("UPDATE device_credential SET key_hash=NULL,revoked_at=? WHERE emulator_id=? AND key_hash IS NOT NULL",
                utc(time), id) == 1;
    }

    public Optional<Snapshot> find(long id) {
        return jdbc.query("""
                SELECT key_hash IS NOT NULL AS has_key,bound_vehicle_id,bound_company_id,bound_mdn,issued_at,expires_at,revoked_at,bound_assignment_revision
                FROM device_credential WHERE emulator_id=?
                """, (rs, row) -> new Snapshot(rs.getBoolean("has_key"), rs.getLong("bound_vehicle_id"),
                rs.getLong("bound_company_id"), rs.getString("bound_mdn"),
                rs.getTimestamp("issued_at").toLocalDateTime().toInstant(ZoneOffset.UTC),
                rs.getTimestamp("expires_at").toLocalDateTime().toInstant(ZoneOffset.UTC),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toLocalDateTime().toInstant(ZoneOffset.UTC),
                rs.getLong("bound_assignment_revision")),
                id).stream().findFirst();
    }

    public void audit(Binding binding, long actorId, String event, Instant time) {
        jdbc.update("INSERT INTO device_credential_event(emulator_id,company_id,actor_member_id,event_type,occurred_at) VALUES(?,?,?,?,?)",
                binding.emulatorId(), binding.companyId(), actorId, event, utc(time));
    }

    private static LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
