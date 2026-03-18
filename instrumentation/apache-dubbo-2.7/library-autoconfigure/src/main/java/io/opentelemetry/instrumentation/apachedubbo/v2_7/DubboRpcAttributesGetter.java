/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

enum DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest, Result> {
  INSTANCE;

  private static final String UNKNOWN_METHOD_SPAN_NAME = "_OTHER";

  @Override
  public String getRpcSystemName(DubboRequest request) {
    return "dubbo";
  }

  @Override
  public String getSystem(DubboRequest request) {
    return "apache_dubbo";
  }

  @Override
  @Nullable
  public String getService(DubboRequest request) {
    if (request.isUnknownService()) {
      return null;
    }
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Deprecated
  @Override
  public String getMethod(DubboRequest request) {
    if (request.isUnknownService()) {
      return UNKNOWN_METHOD_SPAN_NAME;
    }
    return request.invocation().getMethodName();
  }

  @Override
  @Nullable
  public String getRpcMethod(DubboRequest request) {
    if (request.isUnknownService()) {
      return UNKNOWN_METHOD_SPAN_NAME;
    }
    String service = getService(request);
    String method = request.invocation().getMethodName();
    if (service != null && method != null) {
      return service + "/" + method;
    }
    return null;
  }

  @Override
  @Nullable
  public String getRpcMethodOriginal(DubboRequest request) {
    return request.originalFullMethodName();
  }
}
