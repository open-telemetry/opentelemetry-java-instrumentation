/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static com.alipay.sofa.rpc.common.RpcConstants.HIDE_KEY_PREFIX;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.config.AbstractInterfaceConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

final class TracingFilter extends Filter {

  private final Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
  private final boolean isClientSide;

  /** Hidden key: .otel.async.context otel Asynchronous call context */
  private static final String HIDDEN_KEY_ASYNC_CONTEXT = HIDE_KEY_PREFIX + "otel_async_context";

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
        RpcInternalContext internalCtx = RpcInternalContext.getContext();
        internalCtx.setAttachment(HIDDEN_KEY_ASYNC_CONTEXT, context);
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
    RpcInternalContext internalCtx = RpcInternalContext.getContext();
    Context otelContext = (Context) internalCtx.getAttachment(HIDDEN_KEY_ASYNC_CONTEXT);
    if (otelContext == null) {
      return;
    }
    Throwable error = exception != null ? exception : extractException(response);
    instrumenter.end(otelContext, SofaRpcRequest.create(request), response, error);
  }
}
