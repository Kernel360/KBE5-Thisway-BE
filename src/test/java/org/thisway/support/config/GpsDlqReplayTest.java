package org.thisway.support.config;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.thisway.ops.GpsDlqReplay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GpsDlqReplayTest {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path directory;

    @Test
    void audit는_권한600으로_새파일만_생성하고_기존파일을_덮어쓰지_않는다() throws Exception {
        var path = directory.resolve("audit.jsonl");
        var approval = new GpsDlqReplay.Approval("TICKET-1", "0".repeat(64));
        try (var audit = new GpsDlqReplay.FileAudit(path, approval)) {
            audit.append("INTENT", approval.sha256());
        }
        assertThat(java.nio.file.Files.getPosixFilePermissions(path))
                .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        String original = java.nio.file.Files.readString(path);
        assertThat(original).contains("TICKET-1", "INTENT").doesNotContain("mdn", "password", "latitude");
        assertThatThrownBy(() -> new GpsDlqReplay.FileAudit(path, approval))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(java.nio.file.Files.readString(path)).isEqualTo(original);
    }
    private static final byte[] BODY = ("{\"mdn\":\"fixture\",\"tid\":\"1\",\"mid\":\"1\",\"pv\":\"1\",\"did\":\"1\","
            + "\"oTime\":\"20260906000000\",\"cCnt\":\"1\",\"cList\":[{\"gcd\":\"A\",\"lat\":\"37000000\","
            + "\"lon\":\"127000000\",\"ang\":\"0\",\"spd\":\"0\",\"sum\":\"0\",\"bat\":\"12\"}]}")
            .getBytes(StandardCharsets.UTF_8);

    static String digest(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    private Map<String, Object> identityHeaders() {
        return org.thisway.vehicle.log.infrastructure.GpsMessageIdentity.headers(
                new org.thisway.emulator.credential.DeviceIdentity(1, 2, 3, "fixture", 0));
    }

    private Channel channel(byte[] body, Map<String, Object> headers) throws Exception {
        var channel = mock(Channel.class);
        when(channel.basicGet(RabbitMQConfig.GPS_LOG_DLQ, false)).thenReturn(new GetResponse(
                new Envelope(1L, false, "", ""), new AMQP.BasicProperties.Builder().headers(headers).build(), body, 0));
        return channel;
    }

    private void untouched(Channel channel) throws Exception {
        verify(channel, never()).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void preview는_원문없이_hash만_보이고_publish_ack하지_않는다() throws Exception {
        var channel = channel(BODY, identityHeaders());
        assertThat(GpsDlqReplay.processOne(channel, null, (e, d) -> {})).contains(digest(BODY)).doesNotContain("fixture");
        untouched(channel);
    }

    @Test
    void digest가_다르거나_이미_replay한_메시지는_전송하지_않는다() throws Exception {
        var mismatch = channel(BODY, Map.of());
        assertThatThrownBy(() -> GpsDlqReplay.processOne(mismatch,
                new GpsDlqReplay.Approval("TICKET-1", "0".repeat(64)), (e, d) -> {})).hasMessage("Digest mismatch");
        untouched(mismatch);
        var replayed = channel(BODY, Map.of(GpsDlqReplay.REPLAY_COUNT, 1));
        assertThatThrownBy(() -> GpsDlqReplay.processOne(replayed,
                new GpsDlqReplay.Approval("TICKET-1", digest(BODY)), (e, d) -> {})).hasMessage("Replay limit reached");
        untouched(replayed);
    }

    @Test
    void audit쓰기_실패는_publish를_막는다() throws Exception {
        var channel = channel(BODY, identityHeaders());
        assertThatThrownBy(() -> GpsDlqReplay.processOne(channel,
                new GpsDlqReplay.Approval("TICKET-1", digest(BODY)), (e, d) -> { throw new java.io.IOException("fixture"); }))
                .isInstanceOf(java.io.IOException.class);
        untouched(channel);
    }

    @Test
    void confirm_timeout은_ack하지_않는다() throws Exception {
        var channel = channel(BODY, identityHeaders());
        doThrow(new java.util.concurrent.TimeoutException()).when(channel).waitForConfirmsOrDie(5000);
        assertThatThrownBy(() -> GpsDlqReplay.processOne(channel,
                new GpsDlqReplay.Approval("TICKET-1", digest(BODY)), (e, d) -> {}))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void identity없는_legacy_replay는_원본을_ack하거나_발행하지않는다() throws Exception {
        var channel = channel(BODY, Map.of());
        assertThatThrownBy(() -> GpsDlqReplay.processOne(channel,
                new GpsDlqReplay.Approval("TICKET-1", digest(BODY)), (e, d) -> {}))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
        untouched(channel);
    }

    @Test
    void replay는_검증한_identity만_보존하고_임의헤더는_제거한다() throws Exception {
        var headers = new java.util.HashMap<>(identityHeaders());
        headers.put("X-Device-Key", "fixture-secret");
        headers.put("arbitrary", "ignored");
        var channel = channel(BODY, headers);
        GpsDlqReplay.processOne(channel, new GpsDlqReplay.Approval("TICKET-1", digest(BODY)), (e, d) -> {});
        var properties = org.mockito.ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(anyString(), anyString(), eq(true), properties.capture(), eq(BODY));
        assertThat(properties.getValue().getHeaders()).containsAllEntriesOf(identityHeaders())
                .doesNotContainKeys("X-Device-Key", "arbitrary");
        verify(channel).basicAck(1L, false);
    }

    @Test
    void 승인ID_개행과_잘못된_payload는_거부한다() throws Exception {
        assertThatThrownBy(() -> new GpsDlqReplay.Approval("ticket\nforged", "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] invalid = "{}".getBytes(StandardCharsets.UTF_8);
        var channel = channel(invalid, Map.of());
        assertThatThrownBy(() -> GpsDlqReplay.processOne(channel,
                new GpsDlqReplay.Approval("TICKET-1", digest(invalid)), (e, d) -> {})).isInstanceOf(RuntimeException.class);
        untouched(channel);
    }
}
