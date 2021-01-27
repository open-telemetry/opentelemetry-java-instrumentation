/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;
import static io.opentelemetry.api.trace.Span.Kind.SERVER;
import static io.opentelemetry.instrumentation.dubbo.apache.v2_7.DubboInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

@Activate(group = {"consumer", "provider"})
public class TracingFilter implements Filter {
  private final DubboTracer tracer;

  public TracingFilter() {
    this.tracer = new DubboTracer();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (!(invocation instanceof RpcInvocation)) {
      return invoker.invoke(invocation);
    }
    String methodName = invocation.getMethodName();
    String interfaceName = invoker.getInterface().getName();
    RpcContext rpcContext = RpcContext.getContext();
    Kind kind = rpcContext.isProviderSide() ? SERVER : CLIENT;
    Context context;
    if (kind.equals(CLIENT)) {
      context = tracer.startClientSpan(interfaceName, methodName);
      GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .inject(context, (RpcInvocation) invocation, SETTER);
    } else {
      context = tracer.startServerSpan(interfaceName, methodName, (RpcInvocation) invocation);
    }
    Span span = Span.fromContext(context);
    Result result;
    boolean isSynchronous = true;
    try (Scope ignored = span.makeCurrent()) {
      result = invoker.invoke(invocation);
      if (kind.equals(CLIENT)) {
        CompletableFuture<Object> future = rpcContext.getCompletableFuture();
        if (future != null) {
          isSynchronous = false;
          future.whenComplete((o, throwable) -> tracer.endSpan(span, result));
        }
      }

    } catch (Throwable e) {
      tracer.endExceptionally(span, e);
      throw e;
    }
    if (isSynchronous) {
      tracer.endSpan(span, result);
    }
    return result;
  }
}
