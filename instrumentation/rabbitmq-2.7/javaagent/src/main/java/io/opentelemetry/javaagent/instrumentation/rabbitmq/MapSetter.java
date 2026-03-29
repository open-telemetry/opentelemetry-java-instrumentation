/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;
import javax.annotation.Nullable;

public enum MapSetter implements TextMapSetter<Map<String, Object>> {
  INSTANCE;

  @Override
  public void set(@Nullable Map<String, Object> carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.put(key, value);
  }
}
