/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.internal;

import io.grpc.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Context.Storage} override which uses OpenTelemetry context as the backing store. Both gRPC
 * and OpenTelemetry contexts refer to each other to ensure that both OTel context propagation
 * mechanisms and gRPC context propagation mechanisms can be used interchangably.
 */
public final class ContextStorageBridge extends Context.Storage {

  private static final Logger logger = Logger.getLogger(ContextStorageBridge.class.getName());

  private static final ContextKey<Context> GRPC_CONTEXT = ContextKey.named("grpc-context");
  private static final Context.Key<io.opentelemetry.context.Context> OTEL_CONTEXT =
      Context.key("otel-context");

  // Because the extension point is void, there is no way to return information about the backing
  // OpenTelemetry context when attaching gRPC context. So the only option is to have this
  // side-channel to keep track of scopes. Because the same context can be attached to multiple
  // threads, we must use a ThreadLocal here - on the bright side it means the map doesn't have to
  // be concurrent. This will add an additional threadlocal lookup when attaching / detaching gRPC
  // context, but not when accessing the current. In many applications, this means a small
  // difference
  // since those operations are rare, but in highly reactive applications where the overhead of
  // ThreadLocal was already a problem, this makes it worse.
  private static final ThreadLocal<WeakHashMap<Context, Deque<Scope>>> contextScopes =
      ThreadLocal.withInitial(WeakHashMap::new);

  @Override
  public void attach(Context toAttach) {
    io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();
    Context current = otelContext.get(GRPC_CONTEXT);

    if (current == toAttach) {
      contextScopes
          .get()
          .computeIfAbsent(toAttach, unused -> new ArrayDeque<>())
          .addLast(Scope.noop());
      return;
    }

    io.opentelemetry.context.Context base = OTEL_CONTEXT.get(toAttach);
    final io.opentelemetry.context.Context newOtelContext;
    if (base != null) {
      // gRPC context which has an OTel context associated with it via a call to
      // ContextStorageOverride.current(). Using it as the base allows it to be propagated together
      // with the gRPC context.
      newOtelContext = base.with(GRPC_CONTEXT, toAttach);
    } else {
      // gRPC context without an OTel context associated with it. This is only possible when
      // attaching a context directly created by Context.ROOT, e.g., Context.ROOT.with(...) which
      // is not common. We go ahead and assume the gRPC context can be reset while using the current
      // OTel context.
      newOtelContext = io.opentelemetry.context.Context.current().with(GRPC_CONTEXT, toAttach);
    }

    Scope scope = newOtelContext.makeCurrent();
    contextScopes.get().computeIfAbsent(toAttach, unused -> new ArrayDeque<>()).addLast(scope);
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();
    Context current = otelContext.get(GRPC_CONTEXT);
    if (current != toDetach) {
      // Log a severe message instead of throwing an exception as the context to attach is assumed
      // to be the correct one and the unbalanced state represents a coding mistake in a lower
      // layer in the stack that cannot be recovered from here.
      logger.log(
          Level.SEVERE,
          "Context was not attached when detaching",
          new Throwable().fillInStackTrace());
    }
    Map<Context, Deque<Scope>> contextStacks = contextScopes.get();
    Deque<Scope> stack = contextStacks.get(toDetach);
    Scope scope = stack.pollLast();
    if (scope == null) {
      logger.log(
          Level.SEVERE,
          "Detaching context which was not attached.",
          new Throwable().fillInStackTrace());
    } else {
      scope.close();
    }
    if (stack.isEmpty()) {
      contextStacks.remove(toDetach);
    }
  }

  @Override
  public Context current() {
    io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();
    Context current = otelContext.get(GRPC_CONTEXT);
    if (current == null) {
      return Context.ROOT.withValue(OTEL_CONTEXT, otelContext);
    }
    // Store the current OTel context in the gRPC context so that gRPC context propagation
    // mechanisms will also propagate the OTel context.
    io.opentelemetry.context.Context previousOtelContext = OTEL_CONTEXT.get(current);
    if (previousOtelContext != otelContext) {
      // This context has already been previously attached and associated with an OTel context. Just
      // create a new context referring to the current OTel context to reflect the current stack.
      // The previous context is unaffected and will continue to live in its own stack.
      return current.withValue(OTEL_CONTEXT, otelContext);
    }
    return current;
  }
}
