/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextKey;
import application.io.opentelemetry.context.ContextStorage;
import application.io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextStorage} which stores the {@link Context} in the user's application inside the
 * {@link Context} in the agent. This allows for context interaction to be maintained between the
 * app and agent.
 */
public class AgentContextStorage implements ContextStorage {

  private static final io.opentelemetry.context.ContextKey<Context> APPLICATION_CONTEXT =
      io.opentelemetry.context.ContextKey.named("otel-context");

  private static final Logger logger = LoggerFactory.getLogger(AgentContextStorage.class);

  public static final AgentContextStorage INSTANCE = new AgentContextStorage();

  public static io.opentelemetry.context.Context getAgentContext(Context applicationContext) {
    if (applicationContext instanceof AgentContextWrapper) {
      return ((AgentContextWrapper) applicationContext).toAgentContext();
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          "unexpected context: {}", applicationContext, new Exception("unexpected context"));
    }
    return io.opentelemetry.context.Context.root();
  }

  @Override
  public Scope attach(Context toAttach) {
    io.opentelemetry.context.Context currentAgentContext =
        io.opentelemetry.context.Context.current();
    Context currentApplicationContext = currentAgentContext.get(APPLICATION_CONTEXT);
    if (currentApplicationContext == null) {
      currentApplicationContext = Context.root();
    }

    if (currentApplicationContext == toAttach) {
      return Scope.noop();
    }

    io.opentelemetry.context.Context newAgentContext;
    if (toAttach instanceof AgentContextWrapper) {
      newAgentContext = ((AgentContextWrapper) toAttach).toAgentContext();
    } else {
      newAgentContext = currentAgentContext.with(APPLICATION_CONTEXT, toAttach);
    }

    return newAgentContext.makeCurrent()::close;
  }

  @Override
  public Context current() {
    io.opentelemetry.context.Context agentContext = io.opentelemetry.context.Context.current();
    Context applicationContext = agentContext.get(APPLICATION_CONTEXT);
    if (applicationContext == null) {
      applicationContext = Context.root();
    }
    return new AgentContextWrapper(io.opentelemetry.context.Context.current(), applicationContext);
  }

  public static class AgentContextWrapper implements Context {
    private final io.opentelemetry.context.Context agentContext;
    private final Context applicationContext;

    public AgentContextWrapper(
        io.opentelemetry.context.Context agentContext, Context applicationContext) {
      this.agentContext = agentContext;
      this.applicationContext = applicationContext;
    }

    io.opentelemetry.context.Context toAgentContext() {
      if (agentContext.get(APPLICATION_CONTEXT) == applicationContext) {
        return agentContext;
      }
      return agentContext.with(APPLICATION_CONTEXT, applicationContext);
    }

    @Override
    public <V> V get(ContextKey<V> key) {
      return applicationContext.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1));
    }

    @Override
    public <V1, V2> Context with(ContextKey<V1> k1, V1 v1, ContextKey<V2> k2, V2 v2) {
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1, k2, v2));
    }

    @Override
    public <V1, V2, V3> Context with(
        ContextKey<V1> k1, V1 v1, ContextKey<V2> k2, V2 v2, ContextKey<V3> k3, V3 v3) {
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1, k2, v2, k3, v3));
    }

    @Override
    public <V1, V2, V3, V4> Context with(
        ContextKey<V1> k1,
        V1 v1,
        ContextKey<V2> k2,
        V2 v2,
        ContextKey<V3> k3,
        V3 v3,
        ContextKey<V4> k4,
        V4 v4) {
      return new AgentContextWrapper(
          agentContext, applicationContext.with(k1, v1, k2, v2, k3, v3, k4, v4));
    }
  }
}
