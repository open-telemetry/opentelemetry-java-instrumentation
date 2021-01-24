/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.dubbo.apache.v2_7.common.DubboHelper;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

@Activate(group = {"provider"})
public class TracingServerFilter implements Filter {

  private final DubboServerTracer tracer;

  public TracingServerFilter() {
    this.tracer = new DubboServerTracer();
  }

  public static TracingServerFilter newInterceptor() {
    return new TracingServerFilter();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (invocation instanceof RpcInvocation) {
      String methodName = invocation.getMethodName();
      Span span = tracer.startSpan(methodName, (RpcInvocation) invocation);
      NetPeerUtils.INSTANCE.setNetPeer(span, RpcContext.getContext().getRemoteAddress());
      DubboHelper.prepareSpan(span, methodName, invoker);
      Context context = Context.current().with(span);
      Result result;
      try (Scope ignored = context.makeCurrent()) {
        result = invoker.invoke(invocation);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
      tracer.endSpan(span, result);
      return result;
    }
    return invoker.invoke(invocation);
  }
}
