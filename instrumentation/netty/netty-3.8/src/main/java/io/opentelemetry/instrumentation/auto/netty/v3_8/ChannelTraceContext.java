/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v3_8;

import io.grpc.Context;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.trace.Span;
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
  private Span clientSpan;
  private Context clientParentContext;
  private Context context;

  public Context getConnectionContext() {
    return connectionContext;
  }

  public void setConnectionContext(Context connectionContinuation) {
    this.connectionContext = connectionContinuation;
  }

  public Span getClientSpan() {
    return clientSpan;
  }

  public void setClientSpan(Span clientSpan) {
    this.clientSpan = clientSpan;
  }

  public Context getClientParentContext() {
    return clientParentContext;
  }

  public void setClientParentContext(Context clientParentContext) {
    this.clientParentContext = clientParentContext;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChannelTraceContext that = (ChannelTraceContext) o;
    return Objects.equals(connectionContext, that.connectionContext)
        && Objects.equals(clientSpan, that.clientSpan)
        && Objects.equals(clientParentContext, that.clientParentContext)
        && Objects.equals(context, that.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionContext, clientSpan, clientParentContext, context);
  }
}
