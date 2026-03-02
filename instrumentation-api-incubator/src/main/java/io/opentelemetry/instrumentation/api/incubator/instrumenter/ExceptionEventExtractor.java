/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InternalExceptionEventExtractor;

/**
 * Extractor that populates the exception event {@link LogRecordBuilder} for a request. This allows
 * instrumentations to set the event name, severity, and any additional attributes on the exception
 * log event.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@FunctionalInterface
public interface ExceptionEventExtractor<REQUEST> extends InternalExceptionEventExtractor<REQUEST> {

  /**
   * Returns an {@link ExceptionEventExtractor} that always sets the given event name and severity.
   */
  static <REQUEST> ExceptionEventExtractor<REQUEST> create(String eventName, Severity severity) {
    return (logRecordBuilder, context, request) -> {
      logRecordBuilder.setEventName(eventName);
      logRecordBuilder.setSeverity(severity);
    };
  }

  /**
   * Populates the exception event {@link LogRecordBuilder} with the event name, severity, and any
   * additional attributes for the given context and request.
   */
  @Override
  void extract(LogRecordBuilder logRecordBuilder, Context context, REQUEST request);
}
