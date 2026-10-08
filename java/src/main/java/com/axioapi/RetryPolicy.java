package com.axioapi;

import java.util.List;
import java.util.function.LongConsumer;

/** 429 is retried for every method; 5xx and network errors only for idempotent ones. */
final class RetryPolicy {
    private static final List<Integer> RETRYABLE_STATUS = List.of(502, 503, 504);
    private static final List<String> IDEMPOTENT_METHODS = List.of("GET", "HEAD", "DELETE");
    private static final double MAX_DELAY_SECONDS = 8.0;
    private static final double MAX_RETRY_AFTER_SECONDS = 30.0;

    private final int maxRetries;
    private final LongConsumer sleeper;

    RetryPolicy(int maxRetries, LongConsumer sleeper) {
        this.maxRetries = Math.max(0, maxRetries);
        this.sleeper = sleeper;
    }

    boolean shouldRetryStatus(int status, String method, int attempt) {
        if (attempt >= maxRetries) {
            return false;
        }
        return status == 429 || (IDEMPOTENT_METHODS.contains(method) && RETRYABLE_STATUS.contains(status));
    }

    boolean shouldRetryConnection(String method, int attempt) {
        return attempt < maxRetries && IDEMPOTENT_METHODS.contains(method);
    }

    void await(int attempt, String retryAfter) {
        sleeper.accept(delayMillis(attempt, retryAfter));
    }

    private static long delayMillis(int attempt, String retryAfter) {
        if (retryAfter != null && retryAfter.matches("\\d+(\\.\\d+)?")) {
            return (long) (Math.min(Double.parseDouble(retryAfter), MAX_RETRY_AFTER_SECONDS) * 1000);
        }
        return (long) (Math.min(0.5 * Math.pow(2, attempt), MAX_DELAY_SECONDS) * 1000);
    }
}
