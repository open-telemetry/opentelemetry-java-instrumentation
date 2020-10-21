/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toApplication;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.Scope;
import application.io.opentelemetry.trace.Span;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.ApplicationScope;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.NoopScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingContextUtils {

  private static final Logger log = LoggerFactory.getLogger(TracingContextUtils.class);

  public static Context withSpan(
      Span applicationSpan,
      Context applicationContext,
      ContextStore<Context, io.opentelemetry.context.Context> contextStore) {
    io.opentelemetry.trace.Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
    if (agentSpan == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected span: {}", applicationSpan, new Exception("unexpected span"));
      }
      return applicationContext;
    }
    io.opentelemetry.context.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return applicationContext;
    }
    io.opentelemetry.context.Context agentUpdatedContext = agentContext.with(agentSpan);
    contextStore.put(applicationContext, agentUpdatedContext);
    return applicationContext;
  }

  public static Span getCurrentSpan() {
    return toApplication(io.opentelemetry.trace.TracingContextUtils.getCurrentSpan());
  }

  public static Span getSpan(
      Context applicationContext,
      ContextStore<Context, io.opentelemetry.context.Context> contextStore) {
    io.opentelemetry.context.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return Span.getInvalid();
    }
    return toApplication(io.opentelemetry.trace.TracingContextUtils.getSpan(agentContext));
  }

  public static Span getSpanWithoutDefault(
      Context applicationContext,
      ContextStore<Context, io.opentelemetry.context.Context> contextStore) {
    io.opentelemetry.context.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return null;
    }
    io.opentelemetry.trace.Span agentSpan =
        io.opentelemetry.trace.TracingContextUtils.getSpanWithoutDefault(agentContext);
    return agentSpan == null ? null : toApplication(agentSpan);
  }

  public static Scope currentContextWith(Span applicationSpan) {
    if (!applicationSpan.getContext().isValid()) {
      // this supports direct usage of Span.getInvalid()
      return new ApplicationScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              io.opentelemetry.trace.Span.getInvalid()));
    }
    if (applicationSpan instanceof ApplicationSpan) {
      return new ApplicationScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              ((ApplicationSpan) applicationSpan).getAgentSpan()));
    }
    if (log.isDebugEnabled()) {
      log.debug("unexpected span: {}", applicationSpan, new Exception("unexpected span"));
    }
    return NoopScope.getInstance();
  }
}
