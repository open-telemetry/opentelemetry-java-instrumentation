/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

final class TracingFilter implements Filter {

  private static final Logger logger = Logger.getLogger(TracingFilter.class.getName());

  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  // Available in Dubbo 2.7.3 and newer.
  @Nullable
  private static final MethodHandle resultWhenCompleteWithContext =
      findVirtual(Result.class, "whenCompleteWithContext", Result.class, BiConsumer.class);

  // Available in Dubbo 2.7.0 and 2.7.1.
  @Nullable
  private static final MethodHandle asyncResultFuture =
      findVirtual(AsyncRpcResult.class, "getResultFuture", CompletableFuture.class);

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

    if (!instrumenter.shouldStart(parentContext, request)) {
      return invoker.invoke(invocation);
    }
    Context context = instrumenter.start(parentContext, request);

    Result result;
    boolean isSynchronous = true;
    try (Scope ignored = context.makeCurrent()) {
      result = invoker.invoke(invocation);
      if (isClientSide) {
        CompletableFuture<Object> future = rpcContext.getCompletableFuture();
        if (future != null) {
          isSynchronous = false;
          future.whenComplete(
              (o, throwable) -> instrumenter.end(context, request, result, throwable));
        }
      } else if (result instanceof AsyncRpcResult) {
        if (registerProviderCompletion(context, request, (AsyncRpcResult) result)) {
          isSynchronous = false;
        }
      }
    } catch (Throwable e) {
      instrumenter.end(context, request, null, e);
      throw e;
    }
    if (isSynchronous) {
      instrumenter.end(context, request, result, result.getException());
    }
    return result;
  }

  private boolean registerProviderCompletion(
      Context context, DubboRequest request, AsyncRpcResult result) {
    BiConsumer<Result, Throwable> completion =
        (response, throwable) -> endProvider(context, request, response, throwable);
    if (resultWhenCompleteWithContext != null) {
      try {
        resultWhenCompleteWithContext.invoke(result, completion);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to register Dubbo provider completion", t);
      }
      return true;
    }
    if (asyncResultFuture != null) {
      try {
        @SuppressWarnings(
            "unchecked") // Dubbo declares getResultFuture() as CompletableFuture<Result>.
        CompletableFuture<Result> future =
            (CompletableFuture<Result>) asyncResultFuture.invoke(result);
        if (future != null) {
          future.whenComplete(completion);
          return true;
        }
      } catch (Throwable t) {
        logger.log(FINE, "Failed to register Dubbo provider completion", t);
      }
    }
    if (result instanceof CompletionStage) {
      @SuppressWarnings("unchecked") // Dubbo 2.7.2 extends CompletableFuture<Result>.
      CompletionStage<Result> future = (CompletionStage<Result>) result;
      future.whenComplete(completion);
      return true;
    }
    return false;
  }

  private void endProvider(
      Context context, DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    try {
      Throwable actualError = error;
      if (actualError == null && response != null) {
        actualError = response.getException();
      }
      instrumenter.end(context, request, response, actualError);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to end Dubbo provider span", t);
    }
  }

  @Nullable
  private static MethodHandle findVirtual(
      Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      return lookup.findVirtual(type, name, MethodType.methodType(returnType, parameterTypes));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }
}
