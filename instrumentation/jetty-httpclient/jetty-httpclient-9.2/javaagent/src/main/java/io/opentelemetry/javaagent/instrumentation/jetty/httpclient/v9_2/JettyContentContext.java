/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpContent;

public final class JettyContentContext {

  private static final ContextKey<JettyContentContext> ACTIVE =
      ContextKey.named("otel-jetty-caller-context");
  private static final VirtualField<HttpContent, JettyContentContext> CONTENT_CONTEXT =
      VirtualField.find(HttpContent.class, JettyContentContext.class);
  private static final ThreadLocal<JettyContentContext> initializingContent = new ThreadLocal<>();
  private static final ThreadLocal<JettyContentContext> activeCallback = new ThreadLocal<>();

  private final Context context;

  JettyContentContext(Context caller) {
    context = caller.with(ACTIVE, this);
  }

  public Context context() {
    return context;
  }

  @Nullable
  public CallbackScope open() {
    // Reentrant Jetty calls must not overwrite a context explicitly chosen by application code.
    if (activeCallback.get() == this || Context.current().get(ACTIVE) == this) {
      return null;
    }
    JettyContentContext previous = activeCallback.get();
    Scope scope = context.makeCurrent();
    activeCallback.set(this);
    return new CallbackScope(scope, previous);
  }

  @Nullable
  public static JettyContentContext get(HttpContent content) {
    return CONTENT_CONTEXT.get(content);
  }

  public static void initialize(HttpContent content) {
    // In Jetty 9.2 a transport failure may call failed() after close(). The non-reused content
    // owns this immutable state for its lifetime, without a reference back to the carrier.
    CONTENT_CONTEXT.set(content, initializingContent.get());
  }

  @Nullable
  public static JettyContentContext initializing(@Nullable JettyContentContext context) {
    JettyContentContext previous = initializingContent.get();
    if (context == null) {
      initializingContent.remove();
    } else {
      initializingContent.set(context);
    }
    return previous;
  }

  public static final class CallbackScope implements AutoCloseable {
    private final Scope scope;
    @Nullable private final JettyContentContext previous;

    private CallbackScope(Scope scope, @Nullable JettyContentContext previous) {
      this.scope = scope;
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) {
        activeCallback.remove();
      } else {
        activeCallback.set(previous);
      }
      scope.close();
    }
  }
}
