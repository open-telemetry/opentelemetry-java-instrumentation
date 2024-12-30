/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

@Activate(group = {"consumer"})
public final class OpenTelemetryClientFilter implements Filter {

  private final Filter delegate;

  public OpenTelemetryClientFilter() {
    delegate = DubboSingletons.CLIENT_FILTER;
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
