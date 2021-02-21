/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class TextMapExtractAdapter implements TextMapGetter<Headers> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(Headers headers) {
    return StreamSupport.stream(headers.spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

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
