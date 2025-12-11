/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.config.AbstractInterfaceConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.exception.SofaTimeOutException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SOFARPC filter that adds OpenTelemetry tracing to RPC calls.
 *
 * <p>This filter handles both synchronous and asynchronous RPC calls:
 *
 * <ul>
 *   <li>Synchronous calls: span is ended immediately after invoke returns
 *   <li>Asynchronous calls: span is stored and ended when onAsyncResponse is called
 * </ul>
 *
 * <p>For local (in-JVM) calls, CLIENT spans are skipped but SERVER spans are kept for
 * observability.
 *
 * <p>Async contexts are automatically cleaned up after 5 minutes to prevent memory leaks.
 */
final class TracingFilter extends Filter {

  private final Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
  private final boolean isClientSide;

  private static final ConcurrentMap<Integer, AsyncContext> asyncContexts =
      new ConcurrentHashMap<>();

  private static final ScheduledExecutorService cleanupExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sofa-rpc-async-context-cleanup");
            t.setDaemon(true);
            return t;
          });

  static {
    cleanupExecutor.scheduleWithFixedDelay(
        TracingFilter::cleanupStaleContexts, 30, 30, TimeUnit.SECONDS);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  cleanupExecutor.shutdown();
                  try {
                    if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                      cleanupExecutor.shutdownNow();
                    }
                  } catch (InterruptedException e) {
                    cleanupExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                  }
                },
                "sofa-rpc-cleanup-shutdown"));
  }

  private static class AsyncContext {
    final Context context;
    final SofaRpcRequest request;
    final Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
    final long timestamp;

    AsyncContext(
        Context context,
        SofaRpcRequest request,
        Instrumenter<SofaRpcRequest, SofaResponse> instrumenter) {
      this.context = context;
      this.request = request;
      this.instrumenter = instrumenter;
      this.timestamp = System.currentTimeMillis();
    }
  }

  private static void cleanupStaleContexts() {
    long now = System.currentTimeMillis();
    long expireTime = 5 * 60 * 1000; // 5 minutes
    asyncContexts
        .entrySet()
        .removeIf(
            entry -> {
              AsyncContext ctx = entry.getValue();
              if (now - ctx.timestamp > expireTime) {
                // End the span with timeout error
                ctx.instrumenter.end(
                    ctx.context,
                    ctx.request,
                    null,
                    new SofaTimeOutException("Async context timeout"));
                return true;
              }
              return false;
            });
  }

  TracingFilter(Instrumenter<SofaRpcRequest, SofaResponse> instrumenter, boolean isClientSide) {
    this.instrumenter = instrumenter;
    this.isClientSide = isClientSide;
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) throws SofaRpcException {
    if (shouldSkipLocalCall(invoker)) {
      return invoker.invoke(request);
    }

    Context parentContext = Context.current();
    SofaRpcRequest sofaRpcRequest = SofaRpcRequest.create(request);

    if (!instrumenter.shouldStart(parentContext, sofaRpcRequest)) {
      return invoker.invoke(request);
    }
    Context context = instrumenter.start(parentContext, sofaRpcRequest);

    SofaResponse response;
    boolean isSynchronous = true;
    try (Scope ignored = context.makeCurrent()) {
      response = invoker.invoke(request);
      if (isClientSide && request.isAsync()) {
        isSynchronous = false;
        int requestKey = System.identityHashCode(request);
        asyncContexts.put(requestKey, new AsyncContext(context, sofaRpcRequest, instrumenter));
      }
    } catch (Throwable e) {
      instrumenter.end(context, sofaRpcRequest, null, e);
      throw e;
    }

    if (isSynchronous) {
      Throwable exception = extractException(response);
      instrumenter.end(context, sofaRpcRequest, response, exception);
    }

    return response;
  }

  private static boolean shouldSkipLocalCall(FilterInvoker invoker) {
    AbstractInterfaceConfig<?, ?> config = invoker.getConfig();

    if (config instanceof ConsumerConfig) {
      ConsumerConfig<?> consumerConfig = (ConsumerConfig<?>) config;

      if (consumerConfig.isInJVM()) {
        return true;
      }

      String directUrl = consumerConfig.getDirectUrl();
      if (directUrl != null
          && (directUrl.startsWith("local://") || directUrl.startsWith("injvm://"))) {
        return true;
      }
    }
    return false;
  }

  private static Throwable extractException(SofaResponse response) {
    if (response == null) {
      return null;
    }

    Object appResponse = response.getAppResponse();
    if (appResponse instanceof Throwable) {
      return (Throwable) appResponse;
    }

    if (response.isError()
        || "true".equals(response.getResponseProp(RemotingConstants.HEAD_RESPONSE_ERROR))) {
      String errorMsg = response.getErrorMsg();
      if (errorMsg != null) {
        return new SofaRpcException(
            com.alipay.sofa.rpc.core.exception.RpcErrorType.SERVER_UNDECLARED_ERROR, errorMsg);
      }
    }

    return null;
  }

  @Override
  // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
  @SuppressWarnings({"rawtypes", "ThrowsUncheckedException"})
  public void onAsyncResponse(
      ConsumerConfig config, SofaRequest request, SofaResponse response, Throwable exception)
      throws SofaRpcException {
    if (!isClientSide) {
      return;
    }

    int requestKey = System.identityHashCode(request);
    AsyncContext asyncContext = asyncContexts.remove(requestKey);
    if (asyncContext == null) {
      return;
    }

    Throwable error = exception != null ? exception : extractException(response);
    instrumenter.end(asyncContext.context, asyncContext.request, response, error);
  }
}
