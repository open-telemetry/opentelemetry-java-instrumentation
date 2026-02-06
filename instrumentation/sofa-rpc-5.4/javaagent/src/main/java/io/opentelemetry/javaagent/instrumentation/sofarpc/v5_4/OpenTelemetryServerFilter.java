/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.ext.Extension;
import com.alipay.sofa.rpc.filter.AutoActive;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;

@Extension(value = "openTelemetryServer", order = -25000)
@AutoActive(providerSide = true)
public final class OpenTelemetryServerFilter extends Filter {

  private final Filter delegate;

  public OpenTelemetryServerFilter() {
    delegate = SofaRpcSingletons.SERVER_FILTER;
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) throws SofaRpcException {
    return delegate.invoke(invoker, request);
  }
}
