/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeHolder;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

final class DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest, Result> {

  private static final VirtualField<RpcInvocation, DubboStatusCodeHolder> statusCodeField =
      VirtualField.find(RpcInvocation.class, DubboStatusCodeHolder.class);

  @Override
  public String getRpcSystemName(DubboRequest request) {
    return "dubbo";
  }

  @Override
  public String getSystem(DubboRequest request) {
    return "apache_dubbo";
  }

  @Override
  public String getService(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Deprecated
  @Override
  public String getMethod(DubboRequest request) {
    return request.invocation().getMethodName();
  }

  @Override
  @Nullable
  public String getRpcMethod(DubboRequest request) {
    String service = getService(request);
    String method = request.invocation().getMethodName();
    if (service != null && method != null) {
      return service + "/" + method;
    }
    return null;
  }

  @Override
  @Nullable
  public String getErrorType(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    // 1. Check VirtualField for status code set by javaagent instrumentation (Dubbo2 or Triple)
    DubboStatusCodeHolder holder = statusCodeField.get(request.invocation());
    if (holder != null && holder.isServerError()) {
      return holder.getStatusCode();
    }

    // 2. Try to extract Triple status code from the exception
    String tripleStatusCode = DubboStatusCodeConverter.extractTripleStatusCode(error);
    if (tripleStatusCode != null && DubboStatusCodeConverter.isTripleServerError(tripleStatusCode)) {
      return tripleStatusCode;
    }

    // Also check result exception for Triple (async case where error might differ from result)
    if (response != null && response.hasException()) {
      tripleStatusCode = DubboStatusCodeConverter.extractTripleStatusCode(response.getException());
      if (tripleStatusCode != null
          && DubboStatusCodeConverter.isTripleServerError(tripleStatusCode)) {
        return tripleStatusCode;
      }
    }

    // 3. Return null - RpcCommonAttributesExtractor will fall back to exception class name
    return null;
  }
}
