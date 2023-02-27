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
import org.apache.kafka.common.header.Header;

enum KafkaConsumerRecordGetter implements TextMapGetter<KafkaProcessRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(KafkaProcessRequest carrier) {
    return StreamSupport.stream(carrier.getRecord().headers().spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public String get(@Nullable KafkaProcessRequest carrier, String key) {
    Header header = carrier.getRecord().headers().lastHeader(key);
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
