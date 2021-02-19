/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;

public class TextMapInjectAdapter implements TextMapSetter<Map<String, Object>> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(Map<String, Object> carrier, String key, String value) {
    carrier.put(key, value);
  }
}
