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

enum KafkaConsumerRecordGetter implements TextMapGetter<ConsumerAndRecord<ConsumerRecord<?, ?>>> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ConsumerAndRecord<ConsumerRecord<?, ?>> carrier) {
    return StreamSupport.stream(carrier.record().headers().spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public String get(@Nullable ConsumerAndRecord<ConsumerRecord<?, ?>> carrier, String key) {
    Header header = carrier.record().headers().lastHeader(key);
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
