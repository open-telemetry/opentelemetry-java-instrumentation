/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.api.baggage.Baggage;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextStorage;
import application.io.opentelemetry.context.Scope;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.logging.Logger;

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

  private static final Logger logger = Logger.getLogger(AgentContextStorage.class.getName());

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
    if (logger.isLoggable(FINE)) {
      logger.log(
          FINE, "unexpected context: " + applicationContext, new Exception("unexpected context"));
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
}
