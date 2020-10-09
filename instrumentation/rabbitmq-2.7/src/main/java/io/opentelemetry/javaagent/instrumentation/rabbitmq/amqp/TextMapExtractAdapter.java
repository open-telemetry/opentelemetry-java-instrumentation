/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rabbitmq.amqp;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Map;

public class TextMapExtractAdapter implements TextMapPropagator.Getter<Map<String, Object>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public String get(Map<String, Object> carrier, String key) {
    Object obj = carrier.get(key);
    return obj == null ? null : obj.toString();
  }
}
