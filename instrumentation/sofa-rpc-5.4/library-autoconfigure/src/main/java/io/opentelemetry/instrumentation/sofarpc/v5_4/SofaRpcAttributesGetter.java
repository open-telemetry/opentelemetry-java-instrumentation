/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

enum SofaRpcAttributesGetter implements RpcAttributesGetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public String getSystem(SofaRpcRequest request) {
    return "sofa_rpc";
  }

  @Override
  @Nullable
  public String getService(SofaRpcRequest request) {
    return request.request().getInterfaceName();
  }

  @Override
  public String getMethod(SofaRpcRequest request) {
    return request.request().getMethodName();
  }
}
