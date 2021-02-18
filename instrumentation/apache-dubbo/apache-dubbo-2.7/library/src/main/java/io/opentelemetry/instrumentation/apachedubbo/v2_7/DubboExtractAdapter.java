/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.dubbo.rpc.RpcInvocation;

class DubboExtractAdapter implements TextMapGetter<RpcInvocation> {

  static final DubboExtractAdapter GETTER = new DubboExtractAdapter();

  @Override
  public Iterable<String> keys(RpcInvocation invocation) {
    return invocation.getAttachments().keySet();
  }

  @Override
  public String get(RpcInvocation carrier, String key) {
    return carrier.getAttachment(key);
  }
}
