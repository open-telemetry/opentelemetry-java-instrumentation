/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.config.AbstractInterfaceConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
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
 * <ul>
 *   <li>Synchronous calls: span is ended immediately after invoke returns</li>
 *   <li>Asynchronous calls: span is stored and ended when onAsyncResponse is called</li>
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

  // Store context and request for async calls (client side only)
  // Use System.identityHashCode as key to avoid storing large SofaRequest objects
  // Note: This may have hash collisions, but the cleanup mechanism handles stale entries
  private static final ConcurrentMap<Integer, AsyncContext> asyncContexts =
      new ConcurrentHashMap<>();

  // Cleanup executor for removing stale async contexts (prevent memory leak)
  private static final ScheduledExecutorService cleanupExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sofa-rpc-async-context-cleanup");
            t.setDaemon(true);
            return t;
          });

  static {
    // Clean up stale async contexts every 30 seconds
    // Entries older than 5 minutes will be removed
    cleanupExecutor.scheduleWithFixedDelay(
        TracingFilter::cleanupStaleContexts, 30, 30, TimeUnit.SECONDS);

    // Register shutdown hook to clean up executor on JVM shutdown
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

  /**
   * Stores context and request information for async RPC calls.
   *
   * <p>This is used to end the span when the async response is received.
   */
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

  /**
   * Cleans up stale async contexts that haven't been completed within the timeout period.
   *
   * <p>This method is called periodically by the cleanup executor. Any async context older than
   * 5 minutes is considered stale and will be removed, with its span ended with a timeout error.
   */
  private static void cleanupStaleContexts() {
    long now = System.currentTimeMillis();
    long expireTime = 5 * 60 * 1000; // 5 minutes
    asyncContexts.entrySet().removeIf(
        entry -> {
          AsyncContext ctx = entry.getValue();
          if (now - ctx.timestamp > expireTime) {
            // End the span with timeout error
            ctx.instrumenter.end(
                ctx.context,
                ctx.request,
                null,
                new SofaRpcException(
                    com.alipay.sofa.rpc.core.exception.RpcErrorType.CLIENT_TIMEOUT,
                    "Async context timeout"));
            return true;
          }
          return false;
        });
  }

  TracingFilter(Instrumenter<SofaRpcRequest, SofaResponse> instrumenter, boolean isClientSide) {
    this.instrumenter = instrumenter;
    this.isClientSide = isClientSide;
  }

  /**
   * Invokes the next filter in the chain and creates/ends spans for tracing.
   *
   * <p>For synchronous calls, the span is ended immediately after the invoke returns. For
   * asynchronous calls (client-side only), the span context is stored and will be ended when
   * {@link #onAsyncResponse} is called.
   *
   * <p>Local calls are skipped on the client side but still traced on the server side.
   *
   * @param invoker the next filter in the chain
   * @param request the SOFARPC request
   * @return the SOFARPC response
   * @throws SofaRpcException if the invocation fails
   */
  @Override
  // Suppress ThrowsUncheckedException: SOFARPC Filter interface declares throws SofaRpcException
  // even though it's an unchecked exception (RuntimeException)
  @SuppressWarnings("ThrowsUncheckedException")
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) throws SofaRpcException {
    // Skip client-side local calls (see shouldSkipLocalCall() for strategy details)
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

      // For client-side async calls, store context and request for later use in onAsyncResponse
      if (isClientSide && request.isAsync()) {
        isSynchronous = false;
        int requestKey = System.identityHashCode(request);
        asyncContexts.put(requestKey, new AsyncContext(context, sofaRpcRequest, instrumenter));
        // Don't end span here, will be ended in onAsyncResponse
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

  /**
   * Determines whether to skip tracing for local (in-JVM) calls.
   *
   * <p>Strategy: Only skip CLIENT spans for local calls, keep SERVER spans for observability.
   * Client-side can reliably detect local calls, but server-side cannot.
   *
   * @param invoker the filter invoker (contains config to determine client/server side)
   * @return true if this is a client-side local call that should be skipped
   */
  private static boolean shouldSkipLocalCall(FilterInvoker invoker) {
    AbstractInterfaceConfig<?, ?> config = invoker.getConfig();
    
    // Only skip on client side - we can reliably detect local calls here
    if (config instanceof ConsumerConfig) {
      ConsumerConfig<?> consumerConfig = (ConsumerConfig<?>) config;
      
      // Check if explicitly marked as in-JVM call
      if (consumerConfig.isInJVM()) {
        return true;
      }
      
      // Check if directUrl indicates a local call protocol
      String directUrl = consumerConfig.getDirectUrl();
      if (directUrl != null && (directUrl.startsWith("local://") || directUrl.startsWith("injvm://"))) {
        return true;
      }
    }
    
    // Server side: Do NOT skip local calls
    // Even if the call originated from a local client, we cannot reliably detect this on the server side
    // because:
    // 1. Local calls may still use network transport (local loopback) with protocols like "bolt"
    // 2. ProviderInfo.getProtocolType() may reflect the transport protocol, not the original client protocol
    // 3. Request properties may not contain reliable indicators of local calls
    // Therefore, we keep server-side spans for all calls to maintain observability
    
    return false;
  }

  /**
   * Extracts exception from SOFARPC response.
   *
   * <p>SOFARPC can return exceptions in multiple ways:
   * <ol>
   *   <li>As Throwable in appResponse (both error and non-error cases)</li>
   *   <li>With isError flag set to true</li>
   *   <li>With responseProp error indicator set</li>
   * </ol>
   *
   * <p>This method checks these cases in order and returns the first exception found.
   *
   * @param response the SOFARPC response
   * @return the exception if found, null otherwise
   */
  private static Throwable extractException(SofaResponse response) {
    if (response == null) {
      return null;
    }

    // First check: appResponse may contain Throwable (both error and non-error cases)
    Object appResponse = response.getAppResponse();
    if (appResponse instanceof Throwable) {
      return (Throwable) appResponse;
    }

    // Second check: isError flag or responseProp error indicator
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

  /**
   * Called when an async RPC call completes.
   *
   * <p>This method retrieves the stored async context and ends the span. If the context is not
   * found (e.g., already cleaned up), the method returns without ending a span.
   *
   * @param config the consumer config
   * @param request the SOFARPC request
   * @param response the SOFARPC response
   * @param exception any exception that occurred
   * @throws SofaRpcException if processing fails
   */
  @Override
  // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
  // Suppress ThrowsUncheckedException: SOFARPC Filter interface declares throws SofaRpcException
  // even though it's an unchecked exception (RuntimeException)
  @SuppressWarnings({"rawtypes", "ThrowsUncheckedException"})
  public void onAsyncResponse(
      ConsumerConfig config, SofaRequest request, SofaResponse response, Throwable exception)
      throws SofaRpcException {
    // This is only called for client-side async calls
    if (!isClientSide) {
      return;
    }

    int requestKey = System.identityHashCode(request);
    AsyncContext asyncContext = asyncContexts.remove(requestKey);
    if (asyncContext == null) {
      // Context may have been cleaned up due to timeout or hash collision
      return;
    }

    Throwable error = exception != null ? exception : extractException(response);
    instrumenter.end(asyncContext.context, asyncContext.request, response, error);
  }
}
