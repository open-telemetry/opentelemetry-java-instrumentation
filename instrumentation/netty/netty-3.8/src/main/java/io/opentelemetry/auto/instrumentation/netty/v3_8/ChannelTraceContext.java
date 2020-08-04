/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.netty.v3_8;

import io.grpc.Context;
import io.opentelemetry.instrumentation.api.ContextStore;
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
