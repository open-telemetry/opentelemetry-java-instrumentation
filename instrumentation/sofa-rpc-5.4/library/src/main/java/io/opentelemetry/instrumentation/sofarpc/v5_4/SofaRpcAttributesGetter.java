/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.exception.RpcErrorType;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

final class SofaRpcAttributesGetter implements RpcAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Override
  public String getRpcSystemName(SofaRpcRequest request) {
    return "sofarpc";
  }

  @Deprecated
  @Override
  public String getSystem(SofaRpcRequest request) {
    return "sofarpc";
  }

  @Override
  @Nullable
  public String getService(SofaRpcRequest request) {
    return request.request().getInterfaceName();
  }

  @Deprecated
  @Override
  @Nullable
  public String getMethod(SofaRpcRequest request) {
    return request.request().getMethodName();
  }

  @Override
  @Nullable
  public String getRpcMethod(SofaRpcRequest request) {
    String service = getService(request);
    String method = request.request().getMethodName();
    if (service != null && method != null) {
      return service + "/" + method;
    }
    return null;
  }

  @Override
  @Nullable
  public String getErrorType(
      SofaRpcRequest request, @Nullable SofaResponse response, @Nullable Throwable error) {
    if (!(error instanceof SofaRpcException)) {
      return null;
    }

    int errorType = ((SofaRpcException) error).getErrorType();
    switch (errorType) {
      case RpcErrorType.SERVER_BUSY:
      case RpcErrorType.SERVER_CLOSED:
      case RpcErrorType.SERVER_NOT_FOUND_INVOKER:
      case RpcErrorType.SERVER_SERIALIZE:
      case RpcErrorType.SERVER_DESERIALIZE:
      case RpcErrorType.SERVER_NETWORK:
      case RpcErrorType.SERVER_BIZ:
      case RpcErrorType.SERVER_FILTER:
      case RpcErrorType.SERVER_UNDECLARED_ERROR:
      case RpcErrorType.CLIENT_TIMEOUT:
      case RpcErrorType.CLIENT_ROUTER:
      case RpcErrorType.CLIENT_SERIALIZE:
      case RpcErrorType.CLIENT_DESERIALIZE:
      case RpcErrorType.CLIENT_NETWORK:
      case RpcErrorType.CLIENT_FILTER:
      case RpcErrorType.CLIENT_UNDECLARED_ERROR:
        return Integer.toString(errorType);
      default:
        return null;
    }
  }
}
