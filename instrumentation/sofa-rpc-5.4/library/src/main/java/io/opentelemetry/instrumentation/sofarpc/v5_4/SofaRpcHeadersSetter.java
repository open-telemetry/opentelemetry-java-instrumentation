/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum SofaRpcHeadersSetter implements TextMapSetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable SofaRpcRequest request, String key, String value) {
    if (request == null) {
      return;
    }
    request.request().addRequestProp(key, value);
  }
}
