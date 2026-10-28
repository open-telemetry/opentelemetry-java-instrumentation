/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

final class RmiServerAttributesGetter implements RpcAttributesGetter<RmiServerRequest, Void> {

  @Override
  public String getRpcSystemName(RmiServerRequest request) {
    return "java_rmi";
  }

  @Override
  @Nullable
  public String getRpcMethod(RmiServerRequest request) {
    return request.rpcMethodName();
  }
}
