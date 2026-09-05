package org.thisway.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ReturnCallback;
import org.thisway.support.config.RabbitMQConfig;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.interfaces.GpsLogRequestValidator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate operational source set; not included in the web application artifact. */
public final class GpsDlqReplay {
    public static final String REPLAY_COUNT = "thisway-replay-count";
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Approval(String ticket, String sha256) {
        public Approval {
            if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{1,64}")
                    || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid approval or digest");
            }
        }
    }

    @FunctionalInterface
    public interface Audit { void append(String event, String digest) throws IOException; }

    public static final class FileAudit implements Audit, AutoCloseable {
        private final FileChannel file;
        private final Approval approval;

        public FileAudit(Path path, Approval approval) throws IOException {
            if (!path.isAbsolute()) throw new IllegalArgumentException("Absolute audit path required");
            this.approval = approval;
            file = FileChannel.open(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }

        @Override
        public void append(String event, String digest) throws IOException {
            byte[] line = JSON.writeValueAsBytes(Map.of("time", Instant.now().toString(),
                    "approval", approval.ticket(), "event", event, "sha256", digest));
            var buffer = ByteBuffer.wrap((new String(line, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }

        @Override
        public void close() throws IOException { file.close(); }
    }

    /** Caller must always close this dedicated channel/connection, including on failure/preview. */
    public static String processOne(Channel channel, Approval approval, Audit audit) throws Exception {
        channel.queueDeclarePassive(RabbitMQConfig.GPS_LOG_DLQ);
        var delivery = channel.basicGet(RabbitMQConfig.GPS_LOG_DLQ, false);
        if (delivery == null) return "EMPTY";
        byte[] body = delivery.getBody();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        if (approval == null) return "PREVIEW sha256=" + digest + " bytes=" + body.length;
        if (!approval.sha256().equals(digest)) throw new IllegalStateException("Digest mismatch");
        if (body.length > 262144) throw new IllegalStateException("Payload too large");
        var headers = delivery.getProps().getHeaders();
        // Any existing marker is treated as already replayed, including malformed values.
        if (headers != null && headers.containsKey(REPLAY_COUNT)) throw new IllegalStateException("Replay limit reached");
        GpsLogRequestValidator.validate(JSON.readValue(body, GpsLogRequest.class));
        channel.queueDeclarePassive(RabbitMQConfig.GPS_LOG_QUEUE);
        channel.exchangeDeclarePassive(RabbitMQConfig.GPS_LOG_EXCHANGE);
        audit.append("INTENT", digest); // A durable audit failure must prevent publication.
        var returned = new AtomicBoolean();
        channel.addReturnListener((ReturnCallback) result -> returned.set(true));
        channel.confirmSelect();
        // Rebuild properties: never copy arbitrary type/expiry/user headers or broker x-death history.
        var properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json").contentEncoding("UTF-8").deliveryMode(2)
                .headers(Map.of(REPLAY_COUNT, 1, "thisway-replay-approval", approval.ticket(),
                        "__TypeId__", GpsLogRequest.class.getName())).build();
        channel.basicPublish(RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY,
                true, properties, body);
        channel.waitForConfirmsOrDie(5000);
        if (returned.get()) throw new IllegalStateException("Replay unroutable");
        audit.append("PUBLISH_CONFIRMED", digest); // A failure here leaves the DLQ delivery unacked.
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        channel.queueDeclarePassive(RabbitMQConfig.GPS_LOG_DLQ); // Same-channel ack ordering barrier.
        audit.append("ACK_COMPLETED", digest);
        return "REPLAYED sha256=" + digest;
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: preview | execute APPROVAL_ID SHA256 ABSOLUTE_NEW_AUDIT_FILE");
            System.out.println("Requires GPS_REPLAY_URI in environment; one message per invocation. Preview requeues on close.");
            return;
        }
        try {
            boolean preview = args.length == 1 && args[0].equals("preview");
            if (!preview && !(args.length == 4 && args[0].equals("execute"))) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            Approval approval = preview ? null : new Approval(args[1], args[2]);
            var factory = new ConnectionFactory();
            String uri = System.getenv("GPS_REPLAY_URI");
            if (uri == null || uri.isBlank()) throw new IllegalArgumentException("Missing broker configuration");
            factory.setUri(uri);
            if (!Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(factory.getHost())) {
                throw new IllegalArgumentException("Use an approved loopback tunnel");
            }
            factory.setAutomaticRecoveryEnabled(false);
            factory.setConnectionTimeout(5000);
            factory.setHandshakeTimeout(5000);
            // New file only: never overwrite or append to somebody else's audit file.
            try (var audit = preview ? null : new FileAudit(Path.of(args[3]), approval)) {
                if (!preview) audit.append("RUN_STARTED", approval.sha256());
                try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                    System.out.println(processOne(channel, approval, audit));
                } catch (Exception failure) {
                    if (!preview) audit.append("FAILED_OR_UNCERTAIN", approval.sha256());
                    throw failure;
                }
            }
        } catch (Exception failure) {
            // Never render broker URI, raw GPS, arbitrary exception message or stack trace.
            System.err.println("Replay stopped (" + failure.getClass().getSimpleName()
                    + "). Inspect restricted audit; delivery may remain unacked or outcome may be uncertain.");
            System.exit(1);
        }
    }
}
