package org.thisway.support.component.streaming;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;
import org.thisway.support.security.utils.JwtTokenProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("sse-browser")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:sse-browser;DB_CLOSE_DELAY=-1")
@DirtiesContext
class SseBrowserIntegrationTest {
    @LocalServerPort int port;
    @Autowired CompanyRepository companies;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleModelRepository models;
    @Autowired JwtTokenProvider tokens;
    @Autowired SseConnection connections;
    @Autowired SseEventSender sender;

    @Test
    void 브라우저가_nginx를_통해_인증된_실시간_데이터를_받고_재구독한다() throws Exception {
        Path frontend = Path.of(System.getProperty("sse.fe.path"));
        Company company = companies.save(Company.builder().name("sse fixture").crn("sse-test")
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
        VehicleModel model = models.save(VehicleModel.builder().manufacturer("fixture")
                .name("fixture").modelYear(2026).build());
        Vehicle vehicle = vehicles.save(Vehicle.builder().company(company).vehicleModel(model)
                .carNumber("SSE-TEST").color("white").mileage(0).powerOn(false).build());
        String token = tokens.generateAccessToken("fixture@example.com",
                Map.of("roles", List.of("MEMBER"), "companyId", company.getId()));
        String foreignToken = tokens.generateAccessToken("foreign@example.com",
                Map.of("roles", List.of("MEMBER"), "companyId", company.getId() + 1000));
        Testcontainers.exposeHostPorts(port);
        String config = """
                events {}
                http {
                  include /etc/nginx/mime.types;
                  access_log off;
                  server {
                    listen 80;
                    root /usr/share/nginx/html;
                    location /api/ {
                      proxy_pass http://host.testcontainers.internal:%d;
                      proxy_http_version 1.1;
                      proxy_set_header Connection "";
                      proxy_buffering off;
                      proxy_cache off;
                      proxy_read_timeout 10s;
                    }
                  }
                }
                """.formatted(port);
        try (GenericContainer<?> proxy = new GenericContainer<>("nginx:1.28.0-alpine")
                .withExposedPorts(80)
                .withCopyToContainer(Transferable.of(config), "/etc/nginx/nginx.conf")
                .withCopyToContainer(Transferable.of("<!doctype html><title>SSE test</title>"),
                        "/usr/share/nginx/html/index.html")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(
                        frontend.resolve("src/utils/authenticatedEventStream.mjs"))),
                        "/usr/share/nginx/html/adapter.js")) {
            proxy.start();
            ProcessBuilder builder = new ProcessBuilder("node", "tests/browser/live-server.mjs")
                    .directory(frontend.toFile()).inheritIO();
            builder.environment().put("SSE_TEST_URL", "http://" + proxy.getHost() + ":" + proxy.getMappedPort(80));
            builder.environment().put("SSE_TEST_TOKEN", token);
            builder.environment().put("SSE_TEST_FOREIGN_TOKEN", foreignToken);
            builder.environment().put("SSE_TEST_VEHICLE", vehicle.getId().toString());
            Process browser = builder.start();
            try {
                // Deliver repeatedly until both initial and reconnected subscriptions have received data.
                await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(100)).until(() -> {
                    sender.sendToPrefix("vehicle:" + vehicle.getId(), "vehicle_detail_gps_stream", "fixture");
                    return !browser.isAlive();
                });
                assertThat(browser.exitValue()).isZero();
            } finally {
                if (browser.isAlive()) {
                    browser.destroyForcibly();
                    browser.waitFor(5, TimeUnit.SECONDS);
                }
                for (String key : List.copyOf(connections.getAllKeys())) {
                    connections.get(key).ifPresent(emitter -> emitter.complete());
                    connections.remove(key);
                }
            }
        }
    }
}
