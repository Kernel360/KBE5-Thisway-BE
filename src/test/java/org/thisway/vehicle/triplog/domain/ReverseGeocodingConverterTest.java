package org.thisway.vehicle.triplog.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.thisway.support.common.CustomException;
import java.net.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReverseGeocodingConverterTest {
    @Test
    void 응답하지_않는_HTTP_서버는_실제_read_timeout으로_종료한다() throws Exception {
        var converter = new ReverseGeocodingConverter();
        ReflectionTestUtils.setField(converter, "kakaoApiKey", "fixture-only");
        var client = (RestTemplate) ReflectionTestUtils.getField(converter, "restTemplate");
        var realFactory = client.getRequestFactory();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var release = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            var accepted = executor.submit(() -> {
                try (var socket = server.accept()) { release.await(5, TimeUnit.SECONDS); }
                return null;
            });
            try {
                var local = new URI("http", null, server.getInetAddress().getHostAddress(), server.getLocalPort(), "/", null, null);
                client.setRequestFactory((uri, method) -> realFactory.createRequest(local, method));
                assertThatThrownBy(() -> converter.convertToAddress(37.5, 127.0))
                        .isInstanceOf(ResourceAccessException.class).hasRootCauseInstanceOf(SocketTimeoutException.class);
            } finally {
                release.countDown();
                server.close();
                accepted.get(6, TimeUnit.SECONDS);
                executor.shutdownNow();
            }
        }
    }

    @Test
    void 빈_문서와_주소없는_응답을_명시적_실패로_처리한다() {
        for (var payload : java.util.List.of("{\"documents\":[]}", "{\"documents\":[{\"address\":null}]}")) {
            var converter = new ReverseGeocodingConverter();
            var client = (RestTemplate) ReflectionTestUtils.getField(converter, "restTemplate");
            var server = MockRestServiceServer.bindTo(client).build();
            server.expect(anything()).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> converter.convertToAddress(37.5, 127.0)).isInstanceOf(CustomException.class);
            server.verify();
        }
    }
}
