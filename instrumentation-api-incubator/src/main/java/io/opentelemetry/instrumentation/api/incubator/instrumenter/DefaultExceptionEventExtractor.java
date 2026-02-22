/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;

/**
 * {@link ExceptionEventExtractor} that sets the given event name, using {@link Severity#ERROR} when
 * the span is a local root, and {@link Severity#DEBUG} otherwise.
 */
final class DefaultExceptionEventExtractor<REQUEST> implements ExceptionEventExtractor<REQUEST> {

  private final String eventName;

  static <REQUEST> ExceptionEventExtractor<REQUEST> create(String eventName) {
    return new DefaultExceptionEventExtractor<>(eventName);
  }

  private DefaultExceptionEventExtractor(String eventName) {
    this.eventName = eventName;
  }

  @Override
  public void extract(LogRecordBuilder logRecordBuilder, Context context, REQUEST request) {
    logRecordBuilder.setEventName(eventName);
    Span currentSpan = Span.fromContext(context);
    Span localRootSpan = LocalRootSpan.fromContextOrNull(context);
    if (currentSpan.equals(localRootSpan)) {
      logRecordBuilder.setSeverity(Severity.ERROR);
    } else {
      logRecordBuilder.setSeverity(Severity.DEBUG);
    }
  }
}
