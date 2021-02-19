/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import java.time.Duration;

public final class WrapperConfiguration {

  private WrapperConfiguration() {}

  public static final String OTEL_LAMBDA_FLUSH_TIMEOUT_ENV_KEY =
      "OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT";
  public static final Duration OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT = Duration.ofSeconds(1);

  public static final Duration flushTimeout() {
    String lambdaFlushTimeout = System.getenv(OTEL_LAMBDA_FLUSH_TIMEOUT_ENV_KEY);
    if (lambdaFlushTimeout != null && !lambdaFlushTimeout.isEmpty()) {
      try {
        return Duration.ofMillis(Long.parseLong(lambdaFlushTimeout));
      } catch (NumberFormatException nfe) {
        // ignored - default used
      }
    }
    return OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT;
  }
}
