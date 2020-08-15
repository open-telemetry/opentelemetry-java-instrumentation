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

package io.opentelemetry.javaagent.tooling.log;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scope listener that receives the MDC/ThreadContext put and receive methods and update the trace
 * and span reference anytime a new scope is activated or closed.
 */
public class LogContextScopeListener {

  private static final Logger log = LoggerFactory.getLogger(LogContextScopeListener.class);

  private static final String TRACE_ID_KEY = "ot.trace_id";
  private static final String SPAN_ID_KEY = "ot.span_id";

  /** A reference to the log context method that sets a new attribute in the log context */
  private final Method putMethod;

  /** A reference to the log context method that removes an attribute from the log context */
  private final Method removeMethod;

  final Tracer tracer = OpenTelemetry.getTracer("io.opentelemetry.auto");

  public LogContextScopeListener(final Method putMethod, final Method removeMethod) {
    this.putMethod = putMethod;
    this.removeMethod = removeMethod;
  }

  public void afterScopeActivated() {
    try {
      putMethod.invoke(
          null, TRACE_ID_KEY, tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16());
      putMethod.invoke(
          null, SPAN_ID_KEY, tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16());
    } catch (final Exception e) {
      log.debug("Exception setting log context context", e);
    }
  }

  public void afterScopeClosed() {
    try {
      removeMethod.invoke(null, TRACE_ID_KEY);
      removeMethod.invoke(null, SPAN_ID_KEY);
    } catch (final Exception e) {
      log.debug("Exception removing log context context", e);
    }
  }
}
