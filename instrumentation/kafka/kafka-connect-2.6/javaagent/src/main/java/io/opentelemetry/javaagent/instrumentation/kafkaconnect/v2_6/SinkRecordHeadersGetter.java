/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.sink.SinkRecord;

/**
 * Extracts trace context from Kafka Connect SinkRecord headers for distributed tracing.
 * This enables proper trace propagation from the original producer through Kafka Connect processing.
 */
enum SinkRecordHeadersGetter implements TextMapGetter<SinkRecord> {
  INSTANCE;

  @Override
  public Iterable<String> keys(SinkRecord record) {
    if (record.headers() == null) {
      return java.util.Collections.emptyList();
    }
    
    return StreamSupport.stream(record.headers().spliterator(), false)
        .map(Header::key)
        .collect(java.util.stream.Collectors.toList());
  }

  @Override
  @Nullable
  public String get(@Nullable SinkRecord record, String key) {
    if (record == null || record.headers() == null) {
      return null;
    }

    Header header = record.headers().lastWithName(key);
    if (header == null || header.value() == null) {
      return null;
    }

    // Convert header value to string
    Object value = header.value();
    if (value instanceof byte[]) {
      return new String((byte[]) value, StandardCharsets.UTF_8);
    }
    return value.toString();
  }
}
