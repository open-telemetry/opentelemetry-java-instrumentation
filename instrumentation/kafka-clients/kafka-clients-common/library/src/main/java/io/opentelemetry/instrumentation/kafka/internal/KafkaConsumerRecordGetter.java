/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public enum KafkaConsumerRecordGetter implements TextMapGetter<ConsumerRecord<?, ?>> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
    return StreamSupport.stream(carrier.headers().spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public String get(@Nullable ConsumerRecord<?, ?> carrier, String key) {
    Header header = carrier.headers().lastHeader(key);
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
