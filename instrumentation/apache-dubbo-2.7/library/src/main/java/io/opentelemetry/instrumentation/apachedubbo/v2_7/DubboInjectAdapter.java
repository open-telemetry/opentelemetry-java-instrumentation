/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.dubbo.rpc.RpcContext;

class DubboInjectAdapter implements TextMapSetter<RpcContext> {

  static final DubboInjectAdapter SETTER = new DubboInjectAdapter();

  @Override
  public void set(RpcContext rpcContext, String key, String value) {
    rpcContext.setAttachment(key, value);
  }
}
