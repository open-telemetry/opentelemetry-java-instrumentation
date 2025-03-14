/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otel4s.v0_12;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.otel4s.FiberLocalContextHelper;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import javax.annotation.Nullable;

public class Otel4sFiberContextBridge implements ContextStorage {

  private final ContextStorage agentContextStorage;

  public Otel4sFiberContextBridge(ContextStorage delegate) {
    this.agentContextStorage = delegate;
  }

  @Override
  public Scope attach(Context context) {
    Scope fiberScope = FiberLocalContextHelper.attach(context);
    Scope agentScope = agentContextStorage.attach(context);
    return () -> {
      fiberScope.close();
      agentScope.close();
    };
  }

  @Nullable
  @Override
  public Context current() {
    Context agentContext = agentContextStorage.current();
    Context fiberContext = FiberLocalContextHelper.current();

    if (agentContext == null && fiberContext != null) {
      return fiberContext;
    }

    // if (agentContext != null) {
    //    logger.severe("Got conflicting context. Agent: " + agentContext + ". Fiber: " +
    // fiberContext);
    // }

    return agentContext;
  }

  public static ThreadLocal<Context> contextThreadLocal(
      ThreadLocal<application.io.opentelemetry.context.Context> fiberThreadLocal) {
    return new ThreadLocal<Context>() {
      @Override
      public Context get() {
        return AgentContextStorage.getAgentContext(fiberThreadLocal.get());
      }

      @Override
      public void set(Context value) {
        fiberThreadLocal.set(AgentContextStorage.toApplicationContext(value));
      }
    };
  }
}
