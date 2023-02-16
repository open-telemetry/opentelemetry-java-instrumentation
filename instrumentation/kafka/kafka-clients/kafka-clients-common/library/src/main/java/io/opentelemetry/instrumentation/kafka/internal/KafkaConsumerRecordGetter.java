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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaConsumerRecordGetter implements TextMapGetter<KafkaConsumerRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(KafkaConsumerRequest carrier) {
    return StreamSupport.stream(carrier.getConsumerRecord().headers().spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public String get(@Nullable KafkaConsumerRequest carrier, String key) {
    Header header = carrier.getConsumerRecord().headers().lastHeader(key);
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
