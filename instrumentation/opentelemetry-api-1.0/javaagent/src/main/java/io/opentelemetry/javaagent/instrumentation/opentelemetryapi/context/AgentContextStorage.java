/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import application.io.opentelemetry.api.baggage.Baggage;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextKey;
import application.io.opentelemetry.context.ContextStorage;
import application.io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.baggage.BaggageBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.lang.reflect.Field;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextStorage} which stores the {@link Context} in the user's application inside the
 * {@link Context} in the agent. This allows for context interaction to be maintained between the
 * app and agent.
 *
 * <p>This storage allows for implicit parenting of context to exist between the agent and
 * application by storing the concrete application context in the agent context and returning a
 * wrapper which accesses into this stored concrete context.
 *
 * <p>This storage also makes sure that OpenTelemetry objects are shared within the context. To do
 * this, it recognizes the keys for OpenTelemetry objects (e.g, {@link Span}, {@link Baggage}) and
 * always stores and retrieves them from the agent context, even when accessed from the application.
 * All other accesses are to the concrete application context.
 */
// Annotation doesn't work on some fields due to fully qualified name (no clue why it matters...)
@SuppressWarnings("FieldMissingNullable")
public class AgentContextStorage implements ContextStorage, AutoCloseable {

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

  static final io.opentelemetry.context.ContextKey<Context> APPLICATION_CONTEXT =
      io.opentelemetry.context.ContextKey.named("otel-context");

  static final io.opentelemetry.context.ContextKey<io.opentelemetry.api.trace.Span>
      AGENT_SPAN_CONTEXT_KEY;
  @Nullable static final ContextKey<Span> APPLICATION_SPAN_CONTEXT_KEY;

  static final io.opentelemetry.context.ContextKey<io.opentelemetry.api.baggage.Baggage>
      AGENT_BAGGAGE_CONTEXT_KEY;
  @Nullable static final ContextKey<Baggage> APPLICATION_BAGGAGE_CONTEXT_KEY;

  static {
    io.opentelemetry.context.ContextKey<io.opentelemetry.api.trace.Span> agentSpanContextKey;
    try {
      Class<?> spanContextKey = Class.forName("io.opentelemetry.api.trace.SpanContextKey");
      Field spanContextKeyField = spanContextKey.getDeclaredField("KEY");
      spanContextKeyField.setAccessible(true);
      agentSpanContextKey =
          (io.opentelemetry.context.ContextKey<io.opentelemetry.api.trace.Span>)
              spanContextKeyField.get(null);
    } catch (Throwable t) {
      agentSpanContextKey = null;
    }
    AGENT_SPAN_CONTEXT_KEY = agentSpanContextKey;

    ContextKey<Span> applicationSpanContextKey;
    try {
      Class<?> spanContextKey =
          Class.forName("application.io.opentelemetry.api.trace.SpanContextKey");
      Field spanContextKeyField = spanContextKey.getDeclaredField("KEY");
      spanContextKeyField.setAccessible(true);
      applicationSpanContextKey = (ContextKey<Span>) spanContextKeyField.get(null);
    } catch (Throwable t) {
      applicationSpanContextKey = null;
    }
    APPLICATION_SPAN_CONTEXT_KEY = applicationSpanContextKey;

    io.opentelemetry.context.ContextKey<io.opentelemetry.api.baggage.Baggage>
        agentBaggageContextKey;
    try {
      Class<?> baggageContextKey = Class.forName("io.opentelemetry.api.baggage.BaggageContextKey");
      Field baggageContextKeyField = baggageContextKey.getDeclaredField("KEY");
      baggageContextKeyField.setAccessible(true);
      agentBaggageContextKey =
          (io.opentelemetry.context.ContextKey<io.opentelemetry.api.baggage.Baggage>)
              baggageContextKeyField.get(null);
    } catch (Throwable t) {
      agentBaggageContextKey = null;
    }
    AGENT_BAGGAGE_CONTEXT_KEY = agentBaggageContextKey;

    ContextKey<Baggage> applicationBaggageContextKey;
    try {
      Class<?> baggageContextKey =
          Class.forName("application.io.opentelemetry.api.baggage.BaggageContextKey");
      Field baggageContextKeyField = baggageContextKey.getDeclaredField("KEY");
      baggageContextKeyField.setAccessible(true);
      applicationBaggageContextKey = (ContextKey<Baggage>) baggageContextKeyField.get(null);
    } catch (Throwable t) {
      applicationBaggageContextKey = null;
    }
    APPLICATION_BAGGAGE_CONTEXT_KEY = applicationBaggageContextKey;
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

  @Override
  public void close() throws Exception {
    io.opentelemetry.context.ContextStorage agentStorage =
        io.opentelemetry.context.ContextStorage.get();
    if (agentStorage instanceof AutoCloseable) {
      ((AutoCloseable) agentStorage).close();
    }
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
      V maybeBridged =
          bridgedGet(
              APPLICATION_SPAN_CONTEXT_KEY, AGENT_SPAN_CONTEXT_KEY, Bridging::toApplication, key);
      if (maybeBridged != null) {
        return maybeBridged;
      }
      maybeBridged =
          bridgedGet(
              APPLICATION_BAGGAGE_CONTEXT_KEY,
              AGENT_BAGGAGE_CONTEXT_KEY,
              BaggageBridging::toApplication,
              key);
      if (maybeBridged != null) {
        return maybeBridged;
      }

      return applicationContext.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      Context maybeBridged =
          bridgedWith(
              APPLICATION_SPAN_CONTEXT_KEY,
              AGENT_SPAN_CONTEXT_KEY,
              Bridging::toAgentOrNull,
              k1,
              v1);
      if (maybeBridged != null) {
        return maybeBridged;
      }
      maybeBridged =
          bridgedWith(
              APPLICATION_BAGGAGE_CONTEXT_KEY,
              AGENT_BAGGAGE_CONTEXT_KEY,
              BaggageBridging::toAgent,
              k1,
              v1);
      if (maybeBridged != null) {
        return maybeBridged;
      }
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1));
    }

    @Override
    public String toString() {
      return "{agentContext=" + agentContext + ", applicationContext=" + applicationContext + "}";
    }

    @Nullable
    <ApplicationValue, AgentValue, V> V bridgedGet(
        ContextKey<ApplicationValue> applicationValueReferenceKey,
        io.opentelemetry.context.ContextKey<AgentValue> agentValueReferenceKey,
        Function<AgentValue, ApplicationValue> bridgingFunction,
        ContextKey<V> requestedKey) {
      if (requestedKey == applicationValueReferenceKey) {
        AgentValue agentValue = agentContext.get(agentValueReferenceKey);
        if (agentValue == null) {
          return null;
        }
        ApplicationValue applicationValue = bridgingFunction.apply(agentValue);
        @SuppressWarnings("unchecked")
        V castValue = (V) applicationValue;
        return castValue;
      }
      return null;
    }

    @Nullable
    private <ApplicationValue, AgentValue, V> Context bridgedWith(
        ContextKey<ApplicationValue> applicationValueReferenceKey,
        io.opentelemetry.context.ContextKey<AgentValue> agentValueReferenceKey,
        Function<ApplicationValue, AgentValue> bridgingFunction,
        ContextKey<V> requestedKey,
        V value) {
      if (requestedKey == applicationValueReferenceKey) {
        @SuppressWarnings("unchecked")
        ApplicationValue applicationValue = (ApplicationValue) value;
        AgentValue agentValue = bridgingFunction.apply(applicationValue);
        if (agentValue == null) {
          return this;
        }
        return new AgentContextWrapper(
            agentContext.with(agentValueReferenceKey, agentValue), applicationContext);
      }
      return null;
    }
  }
}
