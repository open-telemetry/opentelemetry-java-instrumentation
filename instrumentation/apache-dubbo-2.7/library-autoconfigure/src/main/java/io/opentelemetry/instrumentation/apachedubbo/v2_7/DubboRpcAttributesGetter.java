/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest, Result> {

  private static final String UNKNOWN_METHOD_SPAN_NAME = "_OTHER";

  private final boolean isClient;

  DubboRpcAttributesGetter(boolean isClient) {
    this.isClient = isClient;
  }

  @Override
  public String getRpcSystemName(DubboRequest request) {
    return "dubbo";
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

  @Nullable
  private static String getService(DubboRequest request) {
    if (request.isUnknownService()) {
      return null;
    }
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  @Nullable
  public String getRpcMethodOriginal(DubboRequest request) {
    return request.originalFullMethodName();
  }

  @Override
  @Nullable
  public String getErrorType(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    String statusCode = DubboRpcStatus.getStatusCode(response, error);
    if (statusCode != null && !"OK".equals(statusCode)) {
      return DubboRpcStatus.isError(statusCode, isClient) ? statusCode : null;
    }
    return DubboRpcStatus.getExceptionType(response, error);
  }
}
