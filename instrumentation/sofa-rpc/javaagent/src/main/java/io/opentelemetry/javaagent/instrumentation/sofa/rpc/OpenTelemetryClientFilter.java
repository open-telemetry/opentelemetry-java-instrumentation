/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.ext.Extension;
import com.alipay.sofa.rpc.filter.AutoActive;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;

@Extension(value = "openTelemetryClient", order = -25000)
@AutoActive(consumerSide = true)
public final class OpenTelemetryClientFilter extends Filter {

  private final Filter delegate;

  public OpenTelemetryClientFilter() {
    delegate = SofaRpcSingletons.CLIENT_FILTER;
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) throws SofaRpcException {
    return delegate.invoke(invoker, request);
  }

  @Override
  // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
  // Suppress ThrowsUncheckedException: SOFARPC Filter interface declares throws SofaRpcException
  // even though it's an unchecked exception (RuntimeException)
  @SuppressWarnings({"rawtypes", "ThrowsUncheckedException"})
  public void onAsyncResponse(ConsumerConfig config, SofaRequest request, SofaResponse response,
      Throwable exception) throws SofaRpcException {
    delegate.onAsyncResponse(config, request, response, exception);
  }
}

