/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.Experimental;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GenAiExceptionEventExtractors {

  /**
   * Configures the GenAI client operation exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setGenAiClientExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName("gen_ai.client.operation.exception");
          logRecordBuilder.setSeverity(Severity.WARN);
        });
    return builder;
  }

  private GenAiExceptionEventExtractors() {}
}
