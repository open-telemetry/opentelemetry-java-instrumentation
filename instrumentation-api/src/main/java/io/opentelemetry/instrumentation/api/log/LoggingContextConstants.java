/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.log;

/**
 * This class contains several constants used in logging libraries' Mapped Diagnostic Context
 * instrumentations.
 *
 * @see org.slf4j.MDC
 * @see org.apache.logging.log4j.ThreadContext
 * @see org.apache.log4j.MDC
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
