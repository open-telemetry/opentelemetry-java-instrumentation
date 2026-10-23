/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

final class RmiServerAttributesGetter implements RpcAttributesGetter<RmiServerRequest, Void> {

  @Override
  public String getSystem(RmiServerRequest request) {
    return "java_rmi";
  }

  @Override
  public String getService(RmiServerRequest request) {
    return request.method().getDeclaringClass().getName();
  }

  @Deprecated
  @Override
  public String getMethod(RmiServerRequest request) {
    return request.method().getName();
  }

  @Override
  @Nullable
  public String getRpcMethod(RmiServerRequest request) {
    return request.rpcMethodName();
  }
}
