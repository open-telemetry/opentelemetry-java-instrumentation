/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.faas.internal;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.Experimental;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class FaasExceptionEventExtractors {

  /**
   * Configures the FaaS invocation exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  public static <REQUEST> void setFaasInvocationExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName("faas.invocation.exception");
          logRecordBuilder.setSeverity(Severity.ERROR);
        });
  }

  private FaasExceptionEventExtractors() {}
}
