/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import io.opentelemetry.context.propagation.TextMapSetter;

enum SofaRpcHeadersSetter implements TextMapSetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public void set(SofaRpcRequest request, String key, String value) {
    // Directly use requestProps for context propagation
    // All requestProps will be automatically serialized to network protocol by SOFARPC
    request.request().addRequestProp(key, value);
  }
}
