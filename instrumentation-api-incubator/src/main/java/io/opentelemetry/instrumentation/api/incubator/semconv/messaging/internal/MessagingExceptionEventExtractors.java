/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

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
  public static <REQUEST> void setMessagingCreateExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    setExceptionEventExtractor(builder, "messaging.create.exception", Severity.WARN);
  }

  /**
   * Configures the messaging send exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  public static <REQUEST> void setMessagingSendExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    setExceptionEventExtractor(builder, "messaging.send.exception", Severity.WARN);
  }

  /**
   * Configures the messaging receive exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  public static <REQUEST> void setMessagingReceiveExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    setExceptionEventExtractor(builder, "messaging.receive.exception", Severity.WARN);
  }

  /**
   * Configures the messaging settle exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  public static <REQUEST> void setMessagingSettleExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    setExceptionEventExtractor(builder, "messaging.settle.exception", Severity.WARN);
  }

  /**
   * Configures the messaging process exception event name and severity. Only takes effect when
   * emitting exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview}
   * flag.
   */
  public static <REQUEST> void setMessagingProcessExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder) {
    setExceptionEventExtractor(builder, "messaging.process.exception", Severity.ERROR);
  }

  private static <REQUEST> void setExceptionEventExtractor(
      InstrumenterBuilder<REQUEST, ?> builder, String eventName, Severity severity) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName(eventName);
          logRecordBuilder.setSeverity(severity);
        });
  }

  private MessagingExceptionEventExtractors() {}
}
