package org.thisway.support.logging;

/** Keep code locations, never exception messages, causes, payloads or rejected values. */
public final class SafeDiagnostics {
    private SafeDiagnostics() { }
    public static String describe(Throwable failure) {
        if (failure == null) return "unknown";
        String site = "unknown";
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().startsWith("org.thisway.")) {
                site = frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                break;
            }
        }
        return failure.getClass().getSimpleName() + "@" + site;
    }
}
