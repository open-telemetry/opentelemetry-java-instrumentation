/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static com.alipay.sofa.rpc.core.exception.RpcErrorType.SERVER_UNDECLARED_ERROR;

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

final class TracingFilter extends Filter {

  private static final String OTEL_CONTEXT_KEY = "otel.context";
  private static final String OTEL_REQUEST_KEY = "otel.request";
  // Instrumenter.end() invokes attribute and metric extractors synchronously.
  private static final ThreadLocal<Boolean> SYNTHETIC_FALLBACK_ERROR = new ThreadLocal<>();

  private final Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
  private final boolean isClientSide;

  TracingFilter(Instrumenter<SofaRpcRequest, SofaResponse> instrumenter, boolean isClientSide) {
    this.instrumenter = instrumenter;
    this.isClientSide = isClientSide;
  }

  @Override
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
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
        request.addRequestProp(OTEL_CONTEXT_KEY, context);
        request.addRequestProp(OTEL_REQUEST_KEY, sofaRpcRequest);
      }
    } catch (Throwable t) {
      end(context, sofaRpcRequest, null, new ExtractedException(t, false));
      throw t;
    }

    if (isSynchronous) {
      end(context, sofaRpcRequest, response, extractException(response));
    }

    return response;
  }

  /**
   * Returns {@code true} if this is an in-JVM (local) consumer invocation that should not be
   * traced. Only checks the client (consumer) side; on the provider side there is no equivalent
   * concept of an "in-JVM" call, so the filter always runs and creates a SERVER span there.
   */
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

  private void end(
      Context context,
      SofaRpcRequest sofaRpcRequest,
      SofaResponse response,
      ExtractedException extractedException) {
    Boolean previous = SYNTHETIC_FALLBACK_ERROR.get();
    SYNTHETIC_FALLBACK_ERROR.set(extractedException.syntheticFallback);
    try {
      instrumenter.end(context, sofaRpcRequest, response, extractedException.error);
    } finally {
      if (previous == null) {
        SYNTHETIC_FALLBACK_ERROR.remove();
      } else {
        SYNTHETIC_FALLBACK_ERROR.set(previous);
      }
    }
  }

  static boolean isSyntheticFallbackError() {
    return Boolean.TRUE.equals(SYNTHETIC_FALLBACK_ERROR.get());
  }

  private static ExtractedException extractException(SofaResponse response) {
    if (response == null) {
      return new ExtractedException(null, false);
    }

    Object appResponse = response.getAppResponse();
    if (appResponse instanceof Throwable) {
      return new ExtractedException((Throwable) appResponse, false);
    }

    if (response.isError()
        || "true".equals(response.getResponseProp(RemotingConstants.HEAD_RESPONSE_ERROR))) {
      String errorMsg = response.getErrorMsg();
      if (errorMsg != null) {
        return new ExtractedException(
            new SofaRpcException(SERVER_UNDECLARED_ERROR, errorMsg), true);
      }
    }

    return new ExtractedException(null, false);
  }

  @Override
  // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
  @SuppressWarnings("rawtypes")
  public void onAsyncResponse(
      ConsumerConfig config, SofaRequest request, SofaResponse response, Throwable exception) {
    if (!isClientSide) {
      return;
    }
    Context context = (Context) request.getRequestProp(OTEL_CONTEXT_KEY);
    SofaRpcRequest sofaRpcRequest = (SofaRpcRequest) request.getRequestProp(OTEL_REQUEST_KEY);
    if (context == null || sofaRpcRequest == null) {
      return;
    }
    ExtractedException extractedException =
        exception != null ? new ExtractedException(exception, false) : extractException(response);
    end(context, sofaRpcRequest, response, extractedException);
  }

  private static final class ExtractedException {
    private final Throwable error;
    private final boolean syntheticFallback;

    private ExtractedException(Throwable error, boolean syntheticFallback) {
      this.error = error;
      this.syntheticFallback = syntheticFallback;
    }
  }
}
