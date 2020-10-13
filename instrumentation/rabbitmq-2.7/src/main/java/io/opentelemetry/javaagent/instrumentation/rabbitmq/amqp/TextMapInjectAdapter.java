/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.amqp;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Map;

public class TextMapInjectAdapter implements TextMapPropagator.Setter<Map<String, Object>> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(Map<String, Object> carrier, String key, String value) {
    carrier.put(key, value);
  }
}
