/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

enum SofaRpcHeadersGetter implements TextMapGetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(SofaRpcRequest request) {
    SofaRequest sofaRequest = request.request();
    Map<String, Object> requestProps = sofaRequest.getRequestProps();
    if (requestProps != null && !requestProps.isEmpty()) {
      return requestProps.keySet();
    }
    return Collections.emptySet();
  }

  @Override
  @Nullable
  public String get(SofaRpcRequest request, String key) {
    SofaRequest sofaRequest = request.request();
    Object value = sofaRequest.getRequestProp(key);
    // Only return String values, ignore other types
    return value instanceof String ? (String) value : null;
  }
}
