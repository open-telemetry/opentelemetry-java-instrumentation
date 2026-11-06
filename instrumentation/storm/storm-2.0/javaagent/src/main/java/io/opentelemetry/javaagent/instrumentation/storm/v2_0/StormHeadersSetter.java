/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum StormHeadersSetter implements TextMapSetter<StormEmit> {
  INSTANCE;

  @Override
  public void set(@Nullable StormEmit emit, String key, String value) {
    if (emit == null) {
      return;
    }
    emit.getHeaders().put(key, value);
  }
}
