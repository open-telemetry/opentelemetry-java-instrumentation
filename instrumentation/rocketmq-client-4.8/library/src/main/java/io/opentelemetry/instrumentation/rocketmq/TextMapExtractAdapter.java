/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Map;

public class TextMapExtractAdapter implements TextMapPropagator.Getter<Map<String, String>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(Map<String, String> carrier) {
    return carrier.keySet();
  }

  @Override
  public String get(Map<String, String> carrier, String key) {
    String obj = carrier.get(key);
    return obj;
  }
}
