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

  @Nullable
  private static String getService(SofaRpcRequest request) {
    return request.request().getInterfaceName();
  }

  @Override
  @Nullable
  public String getErrorType(
      SofaRpcRequest request, @Nullable SofaResponse response, @Nullable Throwable error) {
    if (error != null) {
      String sofaRpcCode = getSofaRpcCode(error);
      return sofaRpcCode != null ? sofaRpcCode : error.getClass().getName();
    }

    if (SofaRpcResponseUtil.isError(response)) {
      return Integer.toString(RpcErrorType.SERVER_UNDECLARED_ERROR);
    }
    return null;
  }

  @Override
  @Nullable
  public String getRpcStatusCode(
      SofaRpcRequest request, @Nullable SofaResponse response, @Nullable Throwable error) {
    return getSofaRpcCode(error);
  }

  @Nullable
  private static String getSofaRpcCode(@Nullable Throwable error) {
    if (!(error instanceof SofaRpcException)) {
      return null;
    }

    int errorType = ((SofaRpcException) error).getErrorType();
    // SOFA RPC defines server error categories in 100-199 and client error categories in 200-299.
    // Accepting only these ranges bounds cardinality while accommodating additional categories.
    // https://github.com/sofastack/sofa-rpc/blob/v5.14.3/core/exception/src/main/java/com/alipay/sofa/rpc/core/exception/RpcErrorType.java
    if (errorType >= 100 && errorType <= 299) {
      return Integer.toString(errorType);
    }
    return null;
  }
}
