/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;

enum DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest> {
  INSTANCE;

  @Override
  public String system(DubboRequest request) {
    return "dubbo";
  }

  @Override
  public String service(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  public String method(DubboRequest request) {
    return request.invocation().getMethodName();
  }
}
