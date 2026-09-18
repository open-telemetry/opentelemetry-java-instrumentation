/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;

public final class JedisClusterCommandContext {
  private static final ScopedThreadValue<JedisClusterCommandContext> currentCommandContext =
      new ScopedThreadValue<>();

  @Nullable private Context context;
  @Nullable private JedisRequest request;
  private int executionDepth;
  private int connectionAcquisitionDepth;

  @Nullable
  public static JedisClusterCommandContext create() {
    return emitStableDatabaseSemconv() ? new JedisClusterCommandContext() : null;
  }

  private JedisClusterCommandContext() {}

  public static ScopedThreadValue<JedisClusterCommandContext> currentCommandContext() {
    return currentCommandContext;
  }

  /**
   * Marks the start of borrowing a cluster connection. Commands the client sends while borrowing,
   * such as the health check that validates a pooled connection, are part of getting the connection
   * rather than operations of their own.
   */
  public static void enterConnectionAcquisition() {
    JedisClusterCommandContext commandContext = currentCommandContext.get();
    if (commandContext != null) {
      commandContext.connectionAcquisitionDepth++;
    }
  }

  /** Marks the end of borrowing a cluster connection. */
  public static void exitConnectionAcquisition() {
    JedisClusterCommandContext commandContext = currentCommandContext.get();
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
    return executionDepth > 0;
  }

  public void enterExecute() {
    executionDepth++;
  }

  public void exitExecute() {
    if (executionDepth > 0) {
      executionDepth--;
    }
  }

  public void capture(@Nullable Context context, JedisRequest request) {
    if (!isExecuting()) {
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
    if (context != null && request != null) {
      instrumenter().end(context, request, null, throwable);
    }
  }
}
