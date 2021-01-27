/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7.client;

import static io.opentelemetry.instrumentation.dubbo.apache.v2_7.client.DubboInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

public class TracingClientFilter implements Filter {

  private final DubboClientTracer tracer;

  public TracingClientFilter() {
    this.tracer = new DubboClientTracer();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (!(invocation instanceof RpcInvocation)) {
      return invoker.invoke(invocation);
    }
    String methodName = invocation.getMethodName();
    String interfaceName = invoker.getInterface().getName();
    Context context = tracer.startSpan(interfaceName, methodName);
    Span span = Span.fromContext(context);
    NetPeerUtils.INSTANCE.setNetPeer(span, RpcContext.getContext().getRemoteAddress());
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, (RpcInvocation) invocation, SETTER);
    Result result;
    try (Scope ignored = span.makeCurrent()) {
      result = invoker.invoke(invocation);
    } catch (Throwable e) {
      tracer.endExceptionally(span, e);
      throw e;
    }
    tracer.endSpan(span, result);
    return result;
  }
}
