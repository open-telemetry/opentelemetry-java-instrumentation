/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNativeResponseStatus;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

final class TracingFilter implements Filter {

  private final Instrumenter<DubboRequest, Result> instrumenter;

  private final boolean isClientSide;

  TracingFilter(Instrumenter<DubboRequest, Result> instrumenter, boolean isClientSide) {
    this.instrumenter = instrumenter;
    this.isClientSide = isClientSide;
  }

  @Override
  @SuppressWarnings("deprecation") // deprecation for RpcContext.getContext()
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    if (!(invocation instanceof RpcInvocation)) {
      return invoker.invoke(invocation);
    }

    RpcContext rpcContext = RpcContext.getContext();
    if (rpcContext.getUrl() == null || "injvm".equals(rpcContext.getUrl().getProtocol())) {
      return invoker.invoke(invocation);
    }

    Context parentContext = Context.current();
    DubboRequest request = DubboRequest.create((RpcInvocation) invocation, rpcContext);
    if (!isClientSide) {
      DubboNativeResponseStatus.associateServerInvocation(request.invocation());
    }

    if (!instrumenter.shouldStart(parentContext, request)) {
      return invoker.invoke(invocation);
    }
    Context context = instrumenter.start(parentContext, request);

    Result result;
    boolean isSynchronous = true;
    Object clientInvocationScope =
        isClientSide ? DubboNativeResponseStatus.enterClientInvocation(request.invocation()) : null;
    try (Scope ignored = context.makeCurrent()) {
      result = invoker.invoke(invocation);
    } catch (Throwable e) {
      if (isClientSide
          || !DubboNativeResponseStatus.deferServerCompletion(
              request.invocation(), () -> end(context, request, null, e))) {
        end(context, request, null, e);
      }
      throw e;
    } finally {
      if (clientInvocationScope != null) {
        DubboNativeResponseStatus.exitClientInvocation(clientInvocationScope);
      }
    }
    if (isClientSide) {
      CompletableFuture<Object> future = rpcContext.getCompletableFuture();
      if (future != null) {
        isSynchronous = false;
        future.whenComplete((o, throwable) -> end(context, request, result, throwable));
      }
    }
    if (isSynchronous) {
      if (isClientSide
          || !DubboNativeResponseStatus.deferServerCompletion(
              request.invocation(), () -> end(context, request, result, result.getException()))) {
        end(context, request, result, result.getException());
      }
    }
    return result;
  }

  private void end(
      Context context, DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    try {
      instrumenter.end(context, request, response, error);
    } finally {
      DubboNativeResponseStatus.clear(request.invocation());
    }
  }
}
