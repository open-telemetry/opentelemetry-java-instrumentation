/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class TextMapExtractAdapter implements TextMapPropagator.Getter<Headers> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public String get(Headers headers, String key) {
    Header header = headers.lastHeader(key);
    if (header == null) {
      return null;
    }
    byte[] value = header.value();
    if (value == null) {
      return null;
    }
    return new String(value, StandardCharsets.UTF_8);
  }
}
