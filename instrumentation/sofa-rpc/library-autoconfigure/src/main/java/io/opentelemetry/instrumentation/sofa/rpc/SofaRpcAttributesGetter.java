/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

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
    // Return interface name (e.g., "com.example.Service")
    // targetServiceUniqueName includes version and uniqueId (e.g., "com.example.Service:1.0:uniqueId")
    return request.request().getInterfaceName();
  }

  @Override
  public String getMethod(SofaRpcRequest request) {
    return request.request().getMethodName();
  }
}
