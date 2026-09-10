/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class JedisClusterCommandContext {
  private static final ThreadLocal<JedisClusterCommandContext> current = new ThreadLocal<>();

  @Nullable private Context context;
  @Nullable private JedisRequest request;
  private boolean executing;

  @Nullable
  public static JedisClusterCommandContext start() {
    if (!emitStableDatabaseSemconv() || current.get() != null) {
      return null;
    }
    JedisClusterCommandContext commandContext = new JedisClusterCommandContext();
    current.set(commandContext);
    return commandContext;
  }

  @Nullable
  public static JedisClusterCommandContext current() {
    return current.get();
  }

  public boolean hasRequest() {
    return request != null;
  }

  public boolean isExecuting() {
    return executing;
  }

  public void enterExecute() {
    executing = true;
  }

  public void exitExecute() {
    executing = false;
  }

  public void capture(@Nullable Context context, JedisRequest request) {
    if (!executing) {
      return;
    }
    if (this.request == null) {
      if (context != null) {
        this.context = context;
        this.request = request;
      }
    } else if (this.request.getOperationName().equals(request.getOperationName())
        && this.request.getQueryText().equals(request.getQueryText())
        && this.request.useLaterPeerAddress(request)
        && !emitStableDatabaseSemconv()) {
      Context spanContext = this.context;
      if (spanContext != null) {
        Span span = Span.fromContext(spanContext);
        span.setAttribute(SERVER_ADDRESS, request.getConnection().getHost());
        span.setAttribute(SERVER_PORT, request.getConnection().getPort());
      }
    }
  }

  public void end(@Nullable Throwable throwable) {
    current.remove();
    if (context != null && request != null) {
      instrumenter().end(context, request, null, throwable);
    }
  }

  private JedisClusterCommandContext() {}
}
