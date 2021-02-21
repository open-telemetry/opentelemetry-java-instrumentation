/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.log;

import io.opentelemetry.api.trace.SpanContext;

/**
 * This class contains several constants used in logging libraries' Mapped Diagnostic Context
 * instrumentations.
 *
 * @see org.slf4j.MDC
 */
public final class LoggingContextConstants {
  /**
   * Key under which the current trace id will be injected into the context data.
   *
   * @see SpanContext#getTraceId()
   */
  public static final String TRACE_ID = "trace_id";
  /**
   * Key under which the current span id will be injected into the context data.
   *
   * @see SpanContext#getSpanId()
   */
  public static final String SPAN_ID = "span_id";
  /**
   * Key under which the current trace flags will be injected into the context data.
   *
   * @see SpanContext#getTraceFlags()
   */
  public static final String TRACE_FLAGS = "trace_flags";

  private LoggingContextConstants() {}
}
