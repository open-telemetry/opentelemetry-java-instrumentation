/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class JedisClusterCommandContext {
  private static final ThreadLocal<JedisClusterCommandContext> current = new ThreadLocal<>();

  @Nullable private Context context;
  @Nullable private JedisRequest request;
  private boolean executing;
  private int connectionAcquisitionDepth;

  @Nullable
  public static JedisClusterCommandContext start() {
    if (!emitStableDatabaseSemconv() || current.get() != null) {
      return null;
    }
    JedisClusterCommandContext commandContext = new JedisClusterCommandContext();
    current.set(commandContext);
    return commandContext;
  }

  private JedisClusterCommandContext() {}

  @Nullable
  public static JedisClusterCommandContext current() {
    return current.get();
  }

  /**
   * Marks the start of borrowing a cluster connection. Commands the client sends while borrowing,
   * such as the health check that validates a pooled connection, are part of getting the connection
   * rather than operations of their own.
   */
  public static void enterConnectionAcquisition() {
    JedisClusterCommandContext commandContext = current.get();
    if (commandContext != null) {
      commandContext.connectionAcquisitionDepth++;
    }
  }

  /** Marks the end of borrowing a cluster connection. */
  public static void exitConnectionAcquisition() {
    JedisClusterCommandContext commandContext = current.get();
    if (commandContext != null && commandContext.connectionAcquisitionDepth > 0) {
      commandContext.connectionAcquisitionDepth--;
    }
  }

  public boolean isAcquiringConnection() {
    return connectionAcquisitionDepth > 0;
  }

  public boolean hasRequest() {
    return request != null;
  }

  public boolean matchesCapturedRequest(JedisRequest request) {
    return this.request != null
        && this.request.getOperationName().equals(request.getOperationName())
        && this.request.getQueryText().equals(request.getQueryText());
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
    } else if (matchesCapturedRequest(request)) {
      this.request.useLaterPeerAddress(request);
    }
  }

  public void end(@Nullable Throwable throwable) {
    current.remove();
    if (context != null && request != null) {
      instrumenter().end(context, request, null, throwable);
    }
  }
}
