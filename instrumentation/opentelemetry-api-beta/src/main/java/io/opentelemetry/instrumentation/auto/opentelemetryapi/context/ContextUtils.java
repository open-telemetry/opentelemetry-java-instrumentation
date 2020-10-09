/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.context;

import application.io.grpc.Context;
import application.io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextUtils {

  private static final Logger log = LoggerFactory.getLogger(ContextUtils.class);

  public static Scope withScopedContext(
      Context context, ContextStore<Context, io.grpc.Context> contextStore) {
    io.grpc.Context agentContext = contextStore.get(context);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return NoopScope.getInstance();
    }

    io.opentelemetry.context.Scope agentScope =
        io.opentelemetry.context.ContextUtils.withScopedContext(agentContext);
    return new ApplicationScope(agentScope);
  }
}
