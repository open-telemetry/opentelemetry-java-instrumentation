/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static com.alipay.sofa.rpc.core.exception.RpcErrorType.SERVER_UNDECLARED_ERROR;

import com.alipay.sofa.rpc.client.ProviderInfo;
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
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

final class TracingFilter extends Filter {

  private static final VirtualField<SofaRequest, AsyncState> ASYNC_STATE_FIELD =
      VirtualField.find(SofaRequest.class, AsyncState.class);

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
    boolean isAsync = isClientSide && request.isAsync();
    if (isAsync) {
      ASYNC_STATE_FIELD.set(request, new AsyncState(instrumenter, context, sofaRpcRequest));
    }

    SofaResponse response;
    try (Scope ignored = context.makeCurrent()) {
      response = invoker.invoke(request);
    } catch (Throwable t) {
      if (isAsync) {
        completeAsyncRequest(request, null, t);
      } else {
        instrumenter.end(context, sofaRpcRequest, null, t);
      }
      throw t;
    }

    if (!isAsync) {
      Throwable exception = extractException(response);
      instrumenter.end(context, sofaRpcRequest, response, exception);
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
        return new SofaRpcException(SERVER_UNDECLARED_ERROR, errorMsg);
      }
    }

    return null;
  }

  @Override
  // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
  @SuppressWarnings("rawtypes")
  public void onAsyncResponse(
      ConsumerConfig config, SofaRequest request, SofaResponse response, Throwable exception) {
    if (!isClientSide) {
      return;
    }
    completeAsyncRequest(request, response, exception);
  }

  // Completes an asynchronous request at most once across transport failures, standard filter
  // callbacks, and custom completion callbacks.
  static void completeAsyncRequest(
      SofaRequest request, @Nullable SofaResponse response, @Nullable Throwable exception) {
    AsyncState asyncState = ASYNC_STATE_FIELD.get(request);
    if (asyncState == null || !asyncState.tryComplete()) {
      return;
    }
    ASYNC_STATE_FIELD.set(request, null);

    Throwable error = exception != null ? exception : extractException(response);
    asyncState.end(request, response, error);
  }

  private static final class AsyncState {

    private final Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
    private final Context context;
    @Nullable private final InetSocketAddress remoteAddress;
    @Nullable private final InetSocketAddress localAddress;
    @Nullable private final ProviderInfo providerInfo;
    private final AtomicBoolean completed = new AtomicBoolean();

    private AsyncState(
        Instrumenter<SofaRpcRequest, SofaResponse> instrumenter,
        Context context,
        SofaRpcRequest request) {
      this.instrumenter = instrumenter;
      this.context = context;
      remoteAddress = request.remoteAddress();
      localAddress = request.localAddress();
      providerInfo = request.providerInfo();
    }

    private boolean tryComplete() {
      return completed.compareAndSet(false, true);
    }

    private void end(
        SofaRequest request, @Nullable SofaResponse response, @Nullable Throwable error) {
      SofaRpcRequest endRequest =
          SofaRpcRequest.create(request, remoteAddress, localAddress, providerInfo);
      instrumenter.end(context, endRequest, response, error);
    }
  }
}
