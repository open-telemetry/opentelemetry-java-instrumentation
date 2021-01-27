/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.dubbo.rpc.RpcInvocation;

class DubboInjectAdapter implements TextMapPropagator.Setter<RpcInvocation> {

  static final DubboInjectAdapter SETTER = new DubboInjectAdapter();

  @Override
  public void set(RpcInvocation rpcInvocation, String key, String value) {
    rpcInvocation.setAttachment(key, value);
  }
}
