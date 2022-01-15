/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;

public enum KafkaHeadersSetter implements TextMapSetter<Headers> {
  INSTANCE;

  @Override
  public void set(Headers headers, String key, String value) {
    headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
