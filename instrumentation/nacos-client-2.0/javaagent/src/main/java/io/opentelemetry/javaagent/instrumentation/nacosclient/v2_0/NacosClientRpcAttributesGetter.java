/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

class NacosClientRpcAttributesGetter implements RpcAttributesGetter<NacosClientRequest, Response> {

  @Override
  public String getRpcSystemName(NacosClientRequest request) {
    return "nacos";
  }

  @Override
  @Nullable
  public String getRpcMethod(NacosClientRequest request) {
    return request.category() + "/" + request.operation();
  }

  @Override
  @Nullable
  public String getErrorType(
      NacosClientRequest request, @Nullable Response response, @Nullable Throwable error) {
    if (response != null && !response.isSuccess()) {
      int errorCode = response.getErrorCode();
      return errorCode == 0 ? "response_error" : String.valueOf(errorCode);
    }
    return error == null ? null : error.getClass().getName();
  }
}
