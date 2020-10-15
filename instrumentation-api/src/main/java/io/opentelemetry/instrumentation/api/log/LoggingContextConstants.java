/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.log;

/**
 * This class contains several constants used in logging libraries' Mapped Diagnostic Context
 * instrumentations.
 *
 * @see org.slf4j.MDC
 */
public final class LoggingContextConstants {
  /** Key under which the current trace id will be injected into the context data. */
  public static final String TRACE_ID = "traceId";
  /** Key under which the current span id will be injected into the context data. */
  public static final String SPAN_ID = "spanId";
  /**
   * Key under which a boolean indicating whether current span is sampled will be injected into the
   * context data.
   */
  public static final String SAMPLED = "sampled";

  private LoggingContextConstants() {}
}
