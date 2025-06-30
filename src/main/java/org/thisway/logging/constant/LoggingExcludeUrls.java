package org.thisway.logging.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoggingExcludeUrls {

    public static final Set<String> EXACT_PATHS = Set.of(
            "/actuator/prometheus",
            "/actuator/health"
    );

    public static final List<String> PREFIX_PATHS = List.of(
            "/actuator"
    );

    public static boolean shouldSkip(String uri) {
        if (EXACT_PATHS.contains(uri)) {
            return true;
        }
        return PREFIX_PATHS.stream().anyMatch(uri::startsWith);
    }
}
