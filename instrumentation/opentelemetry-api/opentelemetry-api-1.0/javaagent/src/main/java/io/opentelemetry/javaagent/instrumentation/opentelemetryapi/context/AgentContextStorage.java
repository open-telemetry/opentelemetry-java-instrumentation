/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ContextStorage;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * ContextStorage which stores the Context in the user's application inside the Context in the
 * agent. This allows for context interaction to be maintained between the app and agent.
 *
 * <p>This storage allows for implicit parenting of context to exist between the agent and
 * application by storing the concrete application context in the agent context and returning a
 * wrapper which accesses into this stored concrete context.
 *
 * <p>This storage also makes sure that OpenTelemetry objects are shared within the context. To do
 * this, it recognizes the keys for OpenTelemetry objects (e.g, Span, Baggage) and always stores and
 * retrieves them from the agent context, even when accessed from the application. All other
 * accesses are to the concrete application context.
 */
// Annotation doesn't work on some fields due to fully qualified name (no clue why it matters...)
@SuppressWarnings("FieldMissingNullable")
public class AgentContextStorage
    implements application.io.opentelemetry.context.ContextStorage, AutoCloseable {

  private static final Logger logger = Logger.getLogger(AgentContextStorage.class.getName());

  // MethodHandle for ContextStorage.root() that was added in 1.5
  private static final MethodHandle CONTEXT_STORAGE_ROOT_HANDLE = getContextStorageRootHandle();

  // unwrapped application root context
  private final application.io.opentelemetry.context.Context applicationRoot;
  // wrapped application root context
  private final application.io.opentelemetry.context.Context root;

  private AgentContextStorage(application.io.opentelemetry.context.ContextStorage delegate) {
    applicationRoot = getRootContext(delegate);
    root = getWrappedRootContext(applicationRoot);
  }

  private static MethodHandle getContextStorageRootHandle() {
    try {
      return MethodHandles.lookup()
          .findVirtual(
              application.io.opentelemetry.context.ContextStorage.class,
              "root",
              MethodType.methodType(application.io.opentelemetry.context.Context.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean has15Api() {
    return CONTEXT_STORAGE_ROOT_HANDLE != null;
  }

  private static application.io.opentelemetry.context.Context getRootContext(
      application.io.opentelemetry.context.ContextStorage contextStorage) {
    if (has15Api()) {
      // when bridging to 1.5 api call ContextStorage.root()
      try {
        return (application.io.opentelemetry.context.Context)
            CONTEXT_STORAGE_ROOT_HANDLE.invoke(contextStorage);
      } catch (Throwable throwable) {
        throw new IllegalStateException("Failed to get root context", throwable);
      }
    } else {
      return RootContextHolder.APPLICATION_ROOT;
    }
  }

  private static application.io.opentelemetry.context.Context getWrappedRootContext(
      application.io.opentelemetry.context.Context rootContext) {
    if (has15Api()) {
      return new AgentContextWrapper(Context.root(), rootContext);
    }
    return RootContextHolder.ROOT;
  }

  public static application.io.opentelemetry.context.Context wrapRootContext(
      application.io.opentelemetry.context.Context rootContext) {
    if (has15Api()) {
      return rootContext;
    }
    return RootContextHolder.getRootContext(rootContext);
  }

  // helper class for keeping track of root context when bridging to api earlier than 1.5
  private static class RootContextHolder {
    // unwrapped application root context
    static final application.io.opentelemetry.context.Context APPLICATION_ROOT =
        application.io.opentelemetry.context.Context.root();
    // wrapped application root context
    static final application.io.opentelemetry.context.Context ROOT =
        new AgentContextWrapper(Context.root(), APPLICATION_ROOT);

    static application.io.opentelemetry.context.Context getRootContext(
        application.io.opentelemetry.context.Context rootContext) {
      // APPLICATION_ROOT is null when this method is called while the static initializer is
      // initializing the value of APPLICATION_ROOT field
      if (RootContextHolder.APPLICATION_ROOT == null) {
        return rootContext;
      }
      return RootContextHolder.ROOT;
    }
  }

  public static Function<
          ? super application.io.opentelemetry.context.ContextStorage,
          ? extends application.io.opentelemetry.context.ContextStorage>
      wrap() {
    return contextStorage -> new AgentContextStorage(contextStorage);
  }

  public static Context getAgentContext(
      application.io.opentelemetry.context.Context applicationContext) {
    if (applicationContext instanceof AgentContextWrapper) {
      return ((AgentContextWrapper) applicationContext).toAgentContext();
    }
    if (logger.isLoggable(FINE)) {
      logger.log(
          FINE, "unexpected context: " + applicationContext, new Exception("unexpected context"));
    }
    return Context.root();
  }

  public static application.io.opentelemetry.context.Context toApplicationContext(
      Context agentContext) {
    return new AgentContextWrapper(agentContext);
  }

  public static application.io.opentelemetry.context.Context newContextWrapper(
      Context agentContext, application.io.opentelemetry.context.Context applicationContext) {
    if (applicationContext instanceof AgentContextWrapper) {
      applicationContext = ((AgentContextWrapper) applicationContext).applicationContext;
    }
    return new AgentContextWrapper(agentContext, applicationContext);
  }

  static final ContextKey<application.io.opentelemetry.context.Context> APPLICATION_CONTEXT =
      ContextKey.named("otel-context");

  @Override
  public application.io.opentelemetry.context.Scope attach(
      application.io.opentelemetry.context.Context toAttach) {
    Context currentAgentContext = Context.current();
    application.io.opentelemetry.context.Context currentApplicationContext =
        currentAgentContext.get(APPLICATION_CONTEXT);
    if (currentApplicationContext == null) {
      currentApplicationContext = applicationRoot;
    }

    Context newAgentContext;
    if (toAttach instanceof AgentContextWrapper) {
      AgentContextWrapper wrapper = (AgentContextWrapper) toAttach;
      if (currentApplicationContext == wrapper.applicationContext
          && currentAgentContext == wrapper.agentContext) {
        return application.io.opentelemetry.context.Scope.noop();
      }
      newAgentContext = wrapper.toAgentContext();
    } else {
      newAgentContext = currentAgentContext.with(APPLICATION_CONTEXT, toAttach);
    }

    return newAgentContext.makeCurrent()::close;
  }

  @Override
  public application.io.opentelemetry.context.Context current() {
    Context agentContext = Context.current();
    application.io.opentelemetry.context.Context applicationContext =
        agentContext.get(APPLICATION_CONTEXT);
    if (applicationContext == null) {
      applicationContext = applicationRoot;
    }
    if (applicationContext == applicationRoot && agentContext == Context.root()) {
      return root;
    }
    return new AgentContextWrapper(agentContext, applicationContext);
  }

  @Override
  public application.io.opentelemetry.context.Context root() {
    return root;
  }

  @Override
  public void close() throws Exception {
    ContextStorage agentStorage = ContextStorage.get();
    if (agentStorage instanceof AutoCloseable) {
      ((AutoCloseable) agentStorage).close();
    }
  }
}
