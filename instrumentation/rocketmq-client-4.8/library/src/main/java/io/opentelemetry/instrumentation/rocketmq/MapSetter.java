/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;

enum MapSetter implements TextMapSetter<Map<String, String>> {
  INSTANCE;

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    carrier.put(key, value);
  }
}
