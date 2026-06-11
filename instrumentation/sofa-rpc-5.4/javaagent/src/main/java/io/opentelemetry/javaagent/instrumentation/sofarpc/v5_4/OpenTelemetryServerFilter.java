/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4.SofaRpcSingletons.serverFilter;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.ext.Extension;
import com.alipay.sofa.rpc.filter.AutoActive;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;

@Extension(value = "openTelemetryServer", order = -25000)
@AutoActive(providerSide = true)
public class OpenTelemetryServerFilter extends Filter {

  private final Filter delegate;

  public OpenTelemetryServerFilter() {
    delegate = serverFilter();
  }

  @Override
  public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
    return delegate.invoke(invoker, request);
  }
}
