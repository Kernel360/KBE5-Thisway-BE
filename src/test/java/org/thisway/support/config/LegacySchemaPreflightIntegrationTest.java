package org.thisway.support.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class LegacySchemaPreflightIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "preflight")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @Test
    void 과거_schema의_알려진_차이를_찾고_업무데이터를_변경하지_않는다() throws Exception {
        var source = database("legacy_fixture");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("infra/dev/mysql/db/00-init-schema.sql"));
        }
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO company(active,created_at,addr_detail,addr_road,contact,crn,gps_cycle,memo,name) "
                + "VALUES(1,'2026-09-05 00:00:00','fixture','fixture','000','fixture',60,'preserve me','fixture')");
        var before = jdbc.queryForList("SELECT * FROM company");
        assertThat(audit(jdbc)).containsAllEntriesOf(Map.of(
                "geofence_time", "LEGACY_TYPO", "statistics_table", "MISSING_TABLE",
                "trip_vehicle_index", "MISSING_OR_DIFFERENT_INDEX",
                "misplaced_trip_index", "LEGACY_WRONG_TABLE",
                "batch_table_set", "MISSING_BATCH_TABLES", "flyway_history_table", "NO_HISTORY"));
        assertThat(jdbc.queryForList("SELECT * FROM company")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE()", Integer.class)).isEqualTo(9);
        // An ambiguous partially repaired schema must not suggest a simple rename.
        jdbc.execute("ALTER TABLE geofence_log ADD COLUMN occurred_time datetime NULL");
        assertThat(audit(jdbc).get("geofence_time")).isEqualTo("STOP_AMBIGUOUS_COLUMNS");
        assertThat(jdbc.queryForList("SELECT * FROM company")).isEqualTo(before);
    }

    @Test
    void fresh_migration에도_history_내용_검토를_요구한다() throws Exception {
        var source = database("fresh_fixture");
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        assertThat(audit(jdbc)).containsAllEntriesOf(Map.of(
                "geofence_time", "CHECKED", "statistics_table", "CHECKED",
                "trip_vehicle_index", "CHECKED", "misplaced_trip_index", "CHECKED",
                "batch_table_set", "CHECKED", "flyway_history_table", "HISTORY_REQUIRES_REVIEW"));
    }

    private DriverManagerDataSource database(String name) {
        String server = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/";
        String options = "?allowPublicKeyRetrieval=true&useSSL=false";
        var admin = new JdbcTemplate(new DriverManagerDataSource(server + "preflight" + options,
                "root", "test-root"));
        // Names are fixed test fixtures, never user input; container is disposable.
        admin.execute("CREATE DATABASE " + name);
        return new DriverManagerDataSource(server + name + options, "root", "test-root");
    }

    @Test
    void V3는_기존_중복_row를_삭제하지_않고_NULL_key로_보존한다() {
        var source = database("v2_upgrade_fixture");
        Flyway.configure().dataSource(source).target("2").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO company(id,active,created_at,addr_detail,addr_road,contact,crn,gps_cycle,memo,name) "
                + "VALUES(1,1,NOW(),'fixture','fixture','000','fixture',60,'fixture','fixture')");
        jdbc.update("INSERT INTO vehicle_model(id,active,created_at,manufacturer,model_year,name) "
                + "VALUES(1,1,NOW(),'fixture',2026,'fixture')");
        jdbc.update("INSERT INTO vehicle(id,active,created_at,car_number,color,mileage,power_on,company_id,vehicle_model_id) "
                + "VALUES(1,1,NOW(),'fixture','white',0,0,1,1)");
        jdbc.update("INSERT INTO gps_log(vehicle_id,mdn,occurred_time) VALUES "
                + "(1,'legacy','2026-09-05 00:00:00'),(1,'legacy','2026-09-05 00:00:00')");
        var before = jdbc.queryForList("SELECT id,vehicle_id,mdn,occurred_time FROM gps_log ORDER BY id");
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id,vehicle_id,mdn,occurred_time FROM gps_log ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_log WHERE event_key IS NULL", Integer.class)).isEqualTo(2);
    }

    private Map<String, String> audit(JdbcTemplate jdbc) throws Exception {
        String sql = new ClassPathResource("db/preflight/schema-readiness.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        return jdbc.queryForList(sql).stream().collect(Collectors.toMap(
                row -> (String) row.get("check_name"), row -> (String) row.get("finding")));
    }
}
