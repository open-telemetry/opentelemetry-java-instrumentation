/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

class DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest, Result> {

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
}
