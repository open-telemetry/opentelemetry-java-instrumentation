/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.Objects;

public class ChannelTraceContext {
  public static class Factory implements ContextStore.Factory<ChannelTraceContext> {
    public static final Factory INSTANCE = new Factory();

    @Override
    public ChannelTraceContext create() {
      return new ChannelTraceContext();
    }
  }

  private Context connectionContext;
  private Operation operation; // used for client instrumentation
  private Context context; // used for server instrumentation

  public Context getConnectionContext() {
    return connectionContext;
  }

  public void setConnectionContext(Context connectionContinuation) {
    this.connectionContext = connectionContinuation;
  }

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ChannelTraceContext)) {
      return false;
    }
    ChannelTraceContext other = (ChannelTraceContext) obj;
    return Objects.equals(connectionContext, other.connectionContext)
        && Objects.equals(operation, other.operation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionContext, operation);
  }
}
