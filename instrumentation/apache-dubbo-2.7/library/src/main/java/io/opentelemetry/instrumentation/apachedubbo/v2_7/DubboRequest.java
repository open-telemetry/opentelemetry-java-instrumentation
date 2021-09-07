/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import com.google.auto.value.AutoValue;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

@AutoValue
abstract class DubboRequest {

  static DubboRequest create(RpcInvocation invocation, RpcContext context) {
    return new AutoValue_DubboRequest(invocation, context);
  }

  abstract RpcInvocation invocation();

  abstract RpcContext context();
}
