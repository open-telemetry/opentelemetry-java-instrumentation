/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toApplication;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.trace.Span;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingContextUtils {

  private static final Logger log = LoggerFactory.getLogger(TracingContextUtils.class);

  public static Context withSpan(Span applicationSpan, Context applicationContext) {
    io.opentelemetry.trace.Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
    if (agentSpan == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected span: {}", applicationSpan, new Exception("unexpected span"));
      }
      return applicationContext;
    }
    io.opentelemetry.context.Context agentContext =
        AgentContextStorage.getAgentContext(applicationContext);
    io.opentelemetry.context.Context agentUpdatedContext = agentContext.with(agentSpan);
    return new AgentContextStorage.AgentContextWrapper(agentUpdatedContext, applicationContext);
  }

  public static Span getCurrentSpan() {
    return toApplication(io.opentelemetry.trace.Span.current());
  }

  public static Span getSpan(Context applicationContext) {
    io.opentelemetry.context.Context agentContext =
        AgentContextStorage.getAgentContext(applicationContext);
    return toApplication(io.opentelemetry.trace.Span.fromContext(agentContext));
  }

  public static Span getSpanWithoutDefault(Context applicationContext) {
    io.opentelemetry.context.Context agentContext =
        AgentContextStorage.getAgentContext(applicationContext);
    io.opentelemetry.trace.Span agentSpan =
        io.opentelemetry.trace.Span.fromContextOrNull(agentContext);
    return agentSpan == null ? null : toApplication(agentSpan);
  }
}
