/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_53;

import io.opentelemetry.context.Context;
import java.util.Optional;

/**
 * Converts an explicitly supplied application parent context into the agent context.
 *
 * <p>In the agent the application's {@code io.opentelemetry.context.Context} (referenced here as
 * {@code application.io.opentelemetry.context.Context}) is bridged to the agent context by the
 * opentelemetry-api instrumentation. Making the application context current and reading back the
 * agent context performs that conversion using only public API, so this works in both inline and
 * indy mode without reaching into agent-internal helper classes.
 */
public final class AzureExplicitParentContextHelper {

  // azure-core-tracing-opentelemetry stores the explicit parent context under this key
  private static final String PARENT_TRACE_CONTEXT_KEY = "trace-context";

  public static Optional<Object> bridgeApplicationContext(Object key, Optional<Object> data) {
    if (!PARENT_TRACE_CONTEXT_KEY.equals(key) || data == null || !data.isPresent()) {
      return data;
    }
    Object value = data.get();
    if (!(value instanceof application.io.opentelemetry.context.Context)) {
      return data;
    }
    application.io.opentelemetry.context.Context applicationContext =
        (application.io.opentelemetry.context.Context) value;
    Context agentContext;
    try (application.io.opentelemetry.context.Scope ignored = applicationContext.makeCurrent()) {
      agentContext = Context.current();
    }
    return Optional.of(agentContext);
  }

  private AzureExplicitParentContextHelper() {}
}
