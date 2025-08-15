/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaProducerRequest extends AbstractKafkaRequest {

  private final ProducerRecord<?, ?> record;
  @Nullable private final String clientId;

  public static KafkaProducerRequest create(ProducerRecord<?, ?> record, Producer<?, ?> producer) {
    return new KafkaProducerRequest(
        record, extractClientId(producer), extractBootstrapServers(producer));
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record, String clientId, List<String> bootstrapServers) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers);
  }

  private KafkaProducerRequest(
      ProducerRecord<?, ?> record, String clientId, List<String> bootstrapServers) {
    super(bootstrapServers);
    this.record = record;
    this.clientId = clientId;
  }

  public ProducerRecord<?, ?> getRecord() {
    return record;
  }

  public String getClientId() {
    return clientId;
  }

  private static String extractClientId(Producer<?, ?> producer) {
    try {
      Map<MetricName, ? extends Metric> metrics = producer.metrics();
      Iterator<MetricName> metricIterator = metrics.keySet().iterator();
      return metricIterator.hasNext() ? metricIterator.next().tags().get("client-id") : null;
    } catch (RuntimeException exception) {
      // ExceptionHandlingTest uses a Producer that throws exception on every method call
      return null;
    }
  }

  private static List<String> extractBootstrapServers(Producer<?, ?> producer) {
    return KafkaUtil.getBootstrapServers(producer);
  }
}
