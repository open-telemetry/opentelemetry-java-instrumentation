/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

public class ServerAttributesGetter implements RpcAttributesGetter<SpringRmiServerRequest, Void> {

  @Override
  public String getSystem(SpringRmiServerRequest request) {
    return "spring_rmi";
  }

  @Override
  public String getService(SpringRmiServerRequest request) {
    return request.legacyRequest().declaringClass().getName();
  }

  @Deprecated
  @Override
  public String getMethod(SpringRmiServerRequest request) {
    return request.legacyRequest().methodName();
  }

  @Override
  public String getRpcMethod(SpringRmiServerRequest request) {
    return request.rpcMethod();
  }

  @Override
  @Nullable
  public String getRpcMethodOriginal(SpringRmiServerRequest request) {
    return request.rpcMethodOriginal();
  }
}
