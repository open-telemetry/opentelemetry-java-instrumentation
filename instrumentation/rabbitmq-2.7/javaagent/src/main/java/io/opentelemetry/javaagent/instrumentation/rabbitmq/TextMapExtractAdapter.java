/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;

public class TextMapExtractAdapter implements TextMapGetter<Map<String, Object>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(Map<String, Object> carrier) {
    return carrier.keySet();
  }

  @Override
  public String get(Map<String, Object> carrier, String key) {
    Object obj = carrier.get(key);
    return obj == null ? null : obj.toString();
  }
}
