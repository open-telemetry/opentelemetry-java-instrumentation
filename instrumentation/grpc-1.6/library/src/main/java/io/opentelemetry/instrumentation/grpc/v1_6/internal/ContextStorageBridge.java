/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Context.Storage} override which uses OpenTelemetry context as the backing store. Both gRPC
 * and OpenTelemetry contexts refer to each other to ensure that both OTel context propagation
 * mechanisms and gRPC context propagation mechanisms can be used interchangably.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ContextStorageBridge extends Context.Storage {

  private static final Logger logger = Logger.getLogger(ContextStorageBridge.class.getName());

  private static final ContextKey<Context> GRPC_CONTEXT = ContextKey.named("grpc-context");
  private static final Context.Key<io.opentelemetry.context.Context> OTEL_CONTEXT =
      Context.key("otel-context");
  private static final Context.Key<Scope> OTEL_SCOPE = Context.key("otel-scope");

  @Override
  public Context doAttach(Context toAttach) {
    io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();
    Context current = otelContext.get(GRPC_CONTEXT);

    if (current == null) {
      current = Context.ROOT;
    }

    if (current == toAttach) {
      return current.withValue(OTEL_SCOPE, Scope.noop());
    }

    io.opentelemetry.context.Context base = OTEL_CONTEXT.get(toAttach);
    io.opentelemetry.context.Context newOtelContext;
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
    return current.withValue(OTEL_SCOPE, scope);
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    Scope scope = OTEL_SCOPE.get(toRestore);
    if (scope == null) {
      logger.log(
          Level.SEVERE,
          "Detaching context which was not attached.",
          new Throwable().fillInStackTrace());
    } else {
      scope.close();
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
