/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;

final class TextMapExtractAdapter implements TextMapGetter<Map<String, String>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(Map<String, String> carrier) {
    return carrier.keySet();
  }

  @Override
  public String get(Map<String, String> carrier, String key) {
    return carrier.get(key);
  }
}
