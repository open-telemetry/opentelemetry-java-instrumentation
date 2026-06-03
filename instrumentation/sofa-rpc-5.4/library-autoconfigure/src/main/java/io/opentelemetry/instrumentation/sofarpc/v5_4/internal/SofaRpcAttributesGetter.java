/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.internal;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcRequest;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SofaRpcAttributesGetter
    implements RpcAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Override
  public String getRpcSystemName(SofaRpcRequest request) {
    return "sofa_rpc";
  }

  @Deprecated
  @Override
  public String getSystem(SofaRpcRequest request) {
    return "sofa_rpc";
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
}
