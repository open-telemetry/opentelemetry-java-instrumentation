/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import io.opentelemetry.context.propagation.TextMapSetter;

enum SofaRpcHeadersSetter implements TextMapSetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public void set(SofaRpcRequest request, String key, String value) {
    request.request().addRequestProp(key, value);
  }
}
