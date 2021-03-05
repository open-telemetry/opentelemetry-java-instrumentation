/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.api.trace.SpanKind;
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
public class OpenTelemetryFilter implements Filter {
  private final DubboTracer tracer;

  public OpenTelemetryFilter() {
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
    SpanKind kind = rpcContext.isProviderSide() ? SERVER : CLIENT;
    final Context context;
    if (kind.equals(CLIENT)) {
      context = tracer.startClientSpan(interfaceName, methodName);
      tracer.inject(context, (RpcInvocation) invocation, DubboInjectAdapter.SETTER);
    } else {
      context = tracer.startServerSpan(interfaceName, methodName, (RpcInvocation) invocation);
    }
    final Result result;
    boolean isSynchronous = true;
    try (Scope ignored = context.makeCurrent()) {
      result = invoker.invoke(invocation);
      if (kind.equals(CLIENT)) {
        CompletableFuture<Object> future = rpcContext.getCompletableFuture();
        if (future != null) {
          isSynchronous = false;
          future.whenComplete((o, throwable) -> tracer.end(context, result));
        }
      }

    } catch (Throwable e) {
      tracer.endExceptionally(context, e);
      throw e;
    }
    if (isSynchronous) {
      tracer.end(context, result);
    }
    return result;
  }
}
