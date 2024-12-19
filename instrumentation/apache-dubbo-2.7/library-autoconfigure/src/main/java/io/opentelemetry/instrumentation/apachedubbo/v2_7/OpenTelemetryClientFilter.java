/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

@Activate(group = {"consumer", "provider"})
public final class OpenTelemetryFilter implements Filter {

  private final Filter delegate;

  public OpenTelemetryFilter() {
    delegate = DubboTelemetry.create(GlobalOpenTelemetry.get()).newFilter();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
