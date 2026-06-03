/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.Experimental;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MessagingExceptionEventExtractors {

  /**
   * Configures the messaging create exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setMessagingCreateExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return setExceptionEventExtractor(builder, "messaging.create.exception", Severity.WARN);
  }

  /**
   * Configures the messaging send exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setMessagingSendExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return setExceptionEventExtractor(builder, "messaging.send.exception", Severity.WARN);
  }

  /**
   * Configures the messaging receive exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setMessagingReceiveExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return setExceptionEventExtractor(builder, "messaging.receive.exception", Severity.WARN);
  }

  /**
   * Configures the messaging settle exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setMessagingSettleExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return setExceptionEventExtractor(builder, "messaging.settle.exception", Severity.WARN);
  }

  /**
   * Configures the messaging process exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setMessagingProcessExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    return setExceptionEventExtractor(builder, "messaging.process.exception", Severity.ERROR);
  }

  @CanIgnoreReturnValue
  private static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder, String eventName, Severity severity) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName(eventName);
          logRecordBuilder.setSeverity(severity);
        });
    return builder;
  }

  private MessagingExceptionEventExtractors() {}
}
