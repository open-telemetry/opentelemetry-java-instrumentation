/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentCommandContext;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisServerErrorContext;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class JedisClusterCommandContext implements JedisServerErrorContext {
  @Nullable private Context context;
  @Nullable private JedisRequest request;
  @Nullable private List<Throwable> serverErrors;
  private int executionDepth;
  private int connectionAcquisitionDepth;

  public static JedisClusterCommandContext create() {
    return new JedisClusterCommandContext();
  }

  private JedisClusterCommandContext() {}

  /**
   * Marks the start of borrowing a cluster connection. Commands the client sends while borrowing,
   * such as the health check that validates a pooled connection, are part of getting the connection
   * rather than operations of their own.
   */
  public static void enterConnectionAcquisition() {
    JedisClusterCommandContext commandContext = currentCommandContext().get();
    if (commandContext != null) {
      commandContext.connectionAcquisitionDepth++;
    }
  }

  /** Marks the end of borrowing a cluster connection. */
  public static void exitConnectionAcquisition() {
    JedisClusterCommandContext commandContext = currentCommandContext().get();
    if (commandContext != null && commandContext.connectionAcquisitionDepth > 0) {
      commandContext.connectionAcquisitionDepth--;
    }
  }

  public static void captureCurrentServerError(Throwable throwable) {
    JedisClusterCommandContext commandContext = currentCommandContext().get();
    if (commandContext != null && commandContext.isExecuting()) {
      commandContext.addServerError(throwable);
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
        request.setServerErrorContext(this);
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

  @Override
  public boolean isServerError(Throwable throwable) {
    List<Throwable> serverErrors = this.serverErrors;
    if (serverErrors != null) {
      for (Throwable serverError : serverErrors) {
        if (serverError == throwable) {
          return true;
        }
      }
    }
    return false;
  }

  private void addServerError(Throwable throwable) {
    List<Throwable> serverErrors = this.serverErrors;
    if (serverErrors == null) {
      serverErrors = new ArrayList<>();
      this.serverErrors = serverErrors;
    }
    serverErrors.add(throwable);
  }
}
