package org.thisway.support.logging;

import org.slf4j.MDC;
import org.thisway.support.logging.constant.MdcKeys;
import java.util.UUID;

/** Log correlation only: this does not create distributed tracing spans. */
public final class TraceContext implements AutoCloseable {
    private final String previous = MDC.get(MdcKeys.TRACE_ID);
    private TraceContext(Object candidate) {
        MDC.put(MdcKeys.TRACE_ID, candidate instanceof String value && value.matches("[0-9a-f]{32}")
                ? value : UUID.randomUUID().toString().replace("-", ""));
    }
    public static TraceContext open(Object candidate) { return new TraceContext(candidate); }
    @Override public void close() {
        if (previous == null) MDC.remove(MdcKeys.TRACE_ID);
        else MDC.put(MdcKeys.TRACE_ID, previous);
    }
}
