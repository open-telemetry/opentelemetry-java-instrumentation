/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

final class DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest, Result> {

  private static final String UNKNOWN_METHOD_SPAN_NAME = "_OTHER";
  // Defined RpcException codes across supported Dubbo versions are contiguous from 0 to 13.
  private static final int MAX_KNOWN_EXCEPTION_CODE = 13;

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

  @Override
  @Nullable
  public String getErrorType(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    Throwable cause = error;
    while (cause instanceof CompletionException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (!(cause instanceof RpcException)) {
      return null;
    }
    int code = ((RpcException) cause).getCode();
    if (code >= RpcException.NETWORK_EXCEPTION && code <= MAX_KNOWN_EXCEPTION_CODE) {
      return Integer.toString(code);
    }
    return null;
  }
}
