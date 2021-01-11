/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

public final class WrapperConfiguration {

  private WrapperConfiguration() {}

  public static final String OTEL_LAMBDA_FLUSH_TIMEOUT_ENV_KEY =
      "OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT";
  public static final long OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT = 1;

  public static final long flushTimeout() {
    String lambdaFlushTimeout = System.getenv(OTEL_LAMBDA_FLUSH_TIMEOUT_ENV_KEY);
    if (lambdaFlushTimeout != null && !lambdaFlushTimeout.isEmpty()) {
      try {
        return Long.parseLong(lambdaFlushTimeout);
      } catch (NumberFormatException nfe) {
        // ignored - default used
      }
    }
    return OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT;
  }
}
