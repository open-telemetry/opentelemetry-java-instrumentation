/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dubbo.apache.v2_7.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

@Activate(group = "provider")
public class SimpleFilter implements Filter {
  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    return invoker.invoke(invocation);
  }
}
