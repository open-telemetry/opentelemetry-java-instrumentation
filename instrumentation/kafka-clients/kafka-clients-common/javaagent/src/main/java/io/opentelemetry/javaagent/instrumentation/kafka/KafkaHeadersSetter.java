/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafka;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaHeadersSetter implements TextMapSetter<ProducerRecord<?, ?>> {

  @Override
  public void set(ProducerRecord<?, ?> carrier, String key, String value) {
    carrier.headers().remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
