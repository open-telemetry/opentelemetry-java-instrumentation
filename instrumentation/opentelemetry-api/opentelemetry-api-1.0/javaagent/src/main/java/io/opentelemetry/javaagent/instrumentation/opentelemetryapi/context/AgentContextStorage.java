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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.function.Function;
import javax.annotation.Nullable;
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

  // MethodHandle for ContextStorage.root() that was added in 1.5
  private static final MethodHandle CONTEXT_STORAGE_ROOT_HANDLE = getContextStorageRootHandle();

  // unwrapped application root context
  private final Context applicationRoot;
  // wrapped application root context
  private final Context root;

  private AgentContextStorage(ContextStorage delegate) {
    applicationRoot = getRootContext(delegate);
    root = getWrappedRootContext(applicationRoot);
  }

  private static MethodHandle getContextStorageRootHandle() {
    try {
      return MethodHandles.lookup()
          .findVirtual(ContextStorage.class, "root", MethodType.methodType(Context.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean has15Api() {
    return CONTEXT_STORAGE_ROOT_HANDLE != null;
  }

  private static Context getRootContext(ContextStorage contextStorage) {
    if (has15Api()) {
      // when bridging to 1.5 api call ContextStorage.root()
      try {
        return (Context) CONTEXT_STORAGE_ROOT_HANDLE.invoke(contextStorage);
      } catch (Throwable throwable) {
        throw new IllegalStateException("Failed to get root context", throwable);
      }
    } else {
      return RootContextHolder.APPLICATION_ROOT;
    }
  }

  private static Context getWrappedRootContext(Context rootContext) {
    if (has15Api()) {
      return new AgentContextWrapper(io.opentelemetry.context.Context.root(), rootContext);
    }
    return RootContextHolder.ROOT;
  }

  public static Context wrapRootContext(Context rootContext) {
    if (has15Api()) {
      return rootContext;
    }
    return RootContextHolder.getRootContext(rootContext);
  }

  // helper class for keeping track of root context when bridging to api earlier than 1.5
  private static class RootContextHolder {
    // unwrapped application root context
    static final Context APPLICATION_ROOT = Context.root();
    // wrapped application root context
    static final Context ROOT =
        new AgentContextWrapper(io.opentelemetry.context.Context.root(), APPLICATION_ROOT);

    static Context getRootContext(Context rootContext) {
      // APPLICATION_ROOT is null when this method is called while the static initializer is
      // initializing the value of APPLICATION_ROOT field
      if (RootContextHolder.APPLICATION_ROOT == null) {
        return rootContext;
      }
      return RootContextHolder.ROOT;
    }
  }

  public static Function<? super ContextStorage, ? extends ContextStorage> wrap() {
    return contextStorage -> new AgentContextStorage(contextStorage);
  }

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

  public static Context toApplicationContext(io.opentelemetry.context.Context agentContext) {
    return new AgentContextWrapper(agentContext);
  }

  public static Context newContextWrapper(
      io.opentelemetry.context.Context agentContext, Context applicationContext) {
    if (applicationContext instanceof AgentContextWrapper) {
      applicationContext = ((AgentContextWrapper) applicationContext).applicationContext;
    }
    return new AgentContextWrapper(agentContext, applicationContext);
  }

  static final io.opentelemetry.context.ContextKey<Context> APPLICATION_CONTEXT =
      io.opentelemetry.context.ContextKey.named("otel-context");

  static final ContextKeyBridge<?, ?>[] CONTEXT_KEY_BRIDGES =
      new ContextKeyBridge[] {
        new ContextKeyBridge<Span, io.opentelemetry.api.trace.Span>(
            "application.io.opentelemetry.api.trace.SpanContextKey",
            "io.opentelemetry.api.trace.SpanContextKey",
            Bridging::toApplication,
            Bridging::toAgentOrNull),
        new ContextKeyBridge<>(
            "application.io.opentelemetry.api.baggage.BaggageContextKey",
            "io.opentelemetry.api.baggage.BaggageContextKey",
            BaggageBridging::toApplication,
            BaggageBridging::toAgent),
        bridgeSpanKey("SERVER_KEY"),
        bridgeSpanKey("HTTP_CLIENT_KEY"),
        bridgeSpanKey("RPC_CLIENT_KEY"),
        bridgeSpanKey("DB_CLIENT_KEY"),
        bridgeSpanKey("CLIENT_KEY"),
        bridgeSpanKey("PRODUCER_KEY"),
        bridgeSpanKey("CONSUMER_RECEIVE_KEY"),
        bridgeSpanKey("CONSUMER_PROCESS_KEY"),
      };

  private static ContextKeyBridge<Span, io.opentelemetry.api.trace.Span> bridgeSpanKey(
      String name) {
    return new ContextKeyBridge<>(
        "application.io.opentelemetry.instrumentation.api.instrumenter.SpanKey",
        "io.opentelemetry.instrumentation.api.instrumenter.SpanKey",
        name,
        Bridging::toApplication,
        Bridging::toAgentOrNull);
  }

  @Override
  public Scope attach(Context toAttach) {
    io.opentelemetry.context.Context currentAgentContext =
        io.opentelemetry.context.Context.current();
    Context currentApplicationContext = currentAgentContext.get(APPLICATION_CONTEXT);
    if (currentApplicationContext == null) {
      currentApplicationContext = applicationRoot;
    }

    io.opentelemetry.context.Context newAgentContext;
    if (toAttach instanceof AgentContextWrapper) {
      AgentContextWrapper wrapper = (AgentContextWrapper) toAttach;
      if (currentApplicationContext == wrapper.applicationContext
          && currentAgentContext == wrapper.agentContext) {
        return Scope.noop();
      }
      newAgentContext = wrapper.toAgentContext();
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
      applicationContext = applicationRoot;
    }
    if (applicationContext == applicationRoot
        && agentContext == io.opentelemetry.context.Context.root()) {
      return root;
    }
    return new AgentContextWrapper(agentContext, applicationContext);
  }

  @Override
  public Context root() {
    return root;
  }

  @Override
  public void close() throws Exception {
    io.opentelemetry.context.ContextStorage agentStorage =
        io.opentelemetry.context.ContextStorage.get();
    if (agentStorage instanceof AutoCloseable) {
      ((AutoCloseable) agentStorage).close();
    }
  }

  private static class AgentContextWrapper implements Context {
    final io.opentelemetry.context.Context agentContext;
    final Context applicationContext;

    AgentContextWrapper(io.opentelemetry.context.Context agentContext) {
      this(agentContext, agentContext.get(APPLICATION_CONTEXT));
    }

    AgentContextWrapper(io.opentelemetry.context.Context agentContext, Context applicationContext) {
      if (applicationContext instanceof AgentContextWrapper) {
        throw new IllegalStateException("Expected unwrapped context");
      }
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
      for (ContextKeyBridge<?, ?> bridge : CONTEXT_KEY_BRIDGES) {
        V value = bridge.get(this, key);
        if (value != null) {
          return value;
        }
      }

      return applicationContext.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
      for (ContextKeyBridge<?, ?> bridge : CONTEXT_KEY_BRIDGES) {
        Context context = bridge.with(this, k1, v1);
        if (context != null) {
          return context;
        }
      }
      return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1));
    }

    @Override
    public String toString() {
      return "AgentContextWrapper{agentContext="
          + agentContext
          + ", applicationContext="
          + applicationContext
          + "}";
    }
  }

  static class ContextKeyBridge<APPLICATION, AGENT> {

    private final ContextKey<APPLICATION> applicationContextKey;
    private final io.opentelemetry.context.ContextKey<AGENT> agentContextKey;
    private final Function<APPLICATION, AGENT> toAgent;
    private final Function<AGENT, APPLICATION> toApplication;

    @SuppressWarnings("unchecked")
    ContextKeyBridge(
        String applicationKeyHolderClassName,
        String agentKeyHolderClassName,
        Function<AGENT, APPLICATION> toApplication,
        Function<APPLICATION, AGENT> toAgent) {
      this(applicationKeyHolderClassName, agentKeyHolderClassName, "KEY", toApplication, toAgent);
    }

    ContextKeyBridge(
        String applicationKeyHolderClassName,
        String agentKeyHolderClassName,
        String fieldName,
        Function<AGENT, APPLICATION> toApplication,
        Function<APPLICATION, AGENT> toAgent) {
      this.toApplication = toApplication;
      this.toAgent = toAgent;

      ContextKey<APPLICATION> applicationContextKey;
      try {
        Class<?> applicationKeyHolderClass = Class.forName(applicationKeyHolderClassName);
        Field applicationContextKeyField = applicationKeyHolderClass.getDeclaredField(fieldName);
        applicationContextKeyField.setAccessible(true);
        applicationContextKey = (ContextKey<APPLICATION>) applicationContextKeyField.get(null);
      } catch (Throwable t) {
        applicationContextKey = null;
      }
      this.applicationContextKey = applicationContextKey;

      io.opentelemetry.context.ContextKey<AGENT> agentContextKey;
      try {
        Class<?> agentKeyHolderClass = Class.forName(agentKeyHolderClassName);
        Field agentContextKeyField = agentKeyHolderClass.getDeclaredField(fieldName);
        agentContextKeyField.setAccessible(true);
        agentContextKey =
            (io.opentelemetry.context.ContextKey<AGENT>) agentContextKeyField.get(null);
      } catch (Throwable t) {
        agentContextKey = null;
      }
      this.agentContextKey = agentContextKey;
    }

    @Nullable
    <V> V get(AgentContextWrapper contextWrapper, ContextKey<V> requestedKey) {
      if (requestedKey == applicationContextKey) {
        AGENT agentValue = contextWrapper.agentContext.get(agentContextKey);
        if (agentValue == null) {
          return null;
        }
        APPLICATION applicationValue = toApplication.apply(agentValue);
        @SuppressWarnings("unchecked")
        V castValue = (V) applicationValue;
        return castValue;
      }
      return null;
    }

    @Nullable
    <V> Context with(AgentContextWrapper contextWrapper, ContextKey<V> requestedKey, V value) {
      if (requestedKey == applicationContextKey) {
        @SuppressWarnings("unchecked")
        APPLICATION applicationValue = (APPLICATION) value;
        AGENT agentValue = toAgent.apply(applicationValue);
        if (agentValue == null) {
          return contextWrapper;
        }
        return new AgentContextWrapper(
            contextWrapper.agentContext.with(agentContextKey, agentValue),
            contextWrapper.applicationContext);
      }
      return null;
    }
  }
}
