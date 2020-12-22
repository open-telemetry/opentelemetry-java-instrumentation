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
public class AgentContextStorage implements ContextStorage {

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
  static final ContextKey<Span> APPLICATION_SPAN_CONTEXT_KEY;

  static final io.opentelemetry.context.ContextKey<io.opentelemetry.api.baggage.Baggage>
      AGENT_BAGGAGE_CONTEXT_KEY;
  static final ContextKey<Baggage> APPLICATION_BAGGAGE_CONTEXT_KEY;

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
      if (key == APPLICATION_SPAN_CONTEXT_KEY) {
        io.opentelemetry.api.trace.Span agentSpan = agentContext.get(AGENT_SPAN_CONTEXT_KEY);
        if (agentSpan == null) {
          return null;
        }
        Span applicationSpan = Bridging.toApplication(agentSpan);
        @SuppressWarnings("unchecked")
        V value = (V) applicationSpan;
        return value;
      }
      if (key == APPLICATION_BAGGAGE_CONTEXT_KEY) {
        io.opentelemetry.api.baggage.Baggage agentBaggage =
            agentContext.get(AGENT_BAGGAGE_CONTEXT_KEY);
        if (agentBaggage == null) {
          return null;
        }
        Baggage applicationBaggage = BaggageBridging.toApplication(agentBaggage);
        @SuppressWarnings("unchecked")
        V value = (V) applicationBaggage;
        return value;
      }
      return applicationContext.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      if (k1 == APPLICATION_SPAN_CONTEXT_KEY) {
        Span applicationSpan = (Span) v1;
        io.opentelemetry.api.trace.Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
        if (agentSpan == null) {
          return this;
        }
        return new AgentContextWrapper(
            agentContext.with(AGENT_SPAN_CONTEXT_KEY, agentSpan), applicationContext);
      }
      if (k1 == APPLICATION_BAGGAGE_CONTEXT_KEY) {
        Baggage applicationBaggage = (Baggage) v1;
        io.opentelemetry.api.baggage.Baggage agentBaggage =
            BaggageBridging.toAgent(applicationBaggage);
        return new AgentContextWrapper(
            agentContext.with(AGENT_BAGGAGE_CONTEXT_KEY, agentBaggage), applicationContext);
      }
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1));
    }
  }
}
