/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboRegistryUtil;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

@Activate(
    group = {"consumer"},
    order = -10001)
public final class RegistryAddressCaptureFilter implements Filter {

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    if (invocation instanceof RpcInvocation) {
      DubboRegistryUtil.captureRegistryAddress((RpcInvocation) invocation);
    }
    try {
      return invoker.invoke(invocation);
    } finally {
      DubboRegistryUtil.clearCapturedRegistryAddress();
    }
  }
}
