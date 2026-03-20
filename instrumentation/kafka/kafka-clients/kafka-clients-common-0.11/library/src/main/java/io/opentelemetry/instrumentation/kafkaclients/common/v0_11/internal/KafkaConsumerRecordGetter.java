/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.common.header.Header;

class KafkaConsumerRecordGetter implements TextMapGetter<KafkaProcessRequest> {

  @Override
  public Iterable<String> keys(KafkaProcessRequest carrier) {
    return StreamSupport.stream(carrier.getRecord().headers().spliterator(), false)
        .map(Header::key)
        .collect(toList());
  }

  @Nullable
  @Override
  public String get(@Nullable KafkaProcessRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Header header = carrier.getRecord().headers().lastHeader(key);
    if (header == null) {
      return null;
    }
    byte[] value = header.value();
    if (value == null) {
      return null;
    }
    return new String(value, UTF_8);
  }

  @Override
  public Iterator<String> getAll(@Nullable KafkaProcessRequest carrier, String key) {
    if (carrier == null) {
      return Collections.<String>emptyList().iterator();
    }
    return StreamSupport.stream(carrier.getRecord().headers().headers(key).spliterator(), false)
        .filter(header -> header.value() != null)
        .map(header -> new String(header.value(), UTF_8))
        .iterator();
  }
}
