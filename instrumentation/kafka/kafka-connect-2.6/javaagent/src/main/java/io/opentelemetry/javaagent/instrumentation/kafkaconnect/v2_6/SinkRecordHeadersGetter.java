/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.header.Headers;
import org.apache.kafka.connect.sink.SinkRecord;

enum SinkRecordHeadersGetter implements TextMapGetter<SinkRecord> {
  INSTANCE;

  @Override
  public Iterable<String> keys(SinkRecord record) {
    Headers headers = record.headers();
    List<String> keys = new ArrayList<>();
    for (Header header : headers) {
      keys.add(header.key());
    }
    return keys;
  }

  @Nullable
  @Override
  public String get(@Nullable SinkRecord record, String key) {
    if (record == null) {
      return null;
    }
    Headers headers = record.headers();
    Header header = headers.lastWithName(key);
    if (header == null) {
      return null;
    }
    Object value = header.value();
    if (value instanceof byte[]) {
      return new String((byte[]) value, StandardCharsets.UTF_8);
    }
    return value.toString();
  }
}
