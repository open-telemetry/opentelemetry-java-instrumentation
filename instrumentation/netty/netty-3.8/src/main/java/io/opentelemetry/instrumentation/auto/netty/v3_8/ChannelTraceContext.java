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

  private Span connectionContinuation;
  private Span serverSpan;
  private Span clientSpan;
  private Span clientParentSpan;
  private Context context;

  public Span getConnectionContinuation() {
    return connectionContinuation;
  }

  public void setConnectionContinuation(Span connectionContinuation) {
    this.connectionContinuation = connectionContinuation;
  }

  public Span getServerSpan() {
    return serverSpan;
  }

  public void setServerSpan(Span serverSpan) {
    this.serverSpan = serverSpan;
  }

  public Span getClientSpan() {
    return clientSpan;
  }

  public void setClientSpan(Span clientSpan) {
    this.clientSpan = clientSpan;
  }

  public Span getClientParentSpan() {
    return clientParentSpan;
  }

  public void setClientParentSpan(Span clientParentSpan) {
    this.clientParentSpan = clientParentSpan;
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
    return Objects.equals(connectionContinuation, that.connectionContinuation)
        && Objects.equals(serverSpan, that.serverSpan)
        && Objects.equals(clientSpan, that.clientSpan)
        && Objects.equals(clientParentSpan, that.clientParentSpan)
        && Objects.equals(context, that.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionContinuation, serverSpan, clientSpan, clientParentSpan, context);
  }
}
