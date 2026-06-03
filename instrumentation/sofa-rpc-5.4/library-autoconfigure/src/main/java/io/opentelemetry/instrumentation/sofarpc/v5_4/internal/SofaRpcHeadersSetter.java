/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcRequest;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum SofaRpcHeadersSetter implements TextMapSetter<SofaRpcRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable SofaRpcRequest request, String key, String value) {
    if (request == null) {
      return;
    }
    request.request().addRequestProp(key, value);
  }
}
