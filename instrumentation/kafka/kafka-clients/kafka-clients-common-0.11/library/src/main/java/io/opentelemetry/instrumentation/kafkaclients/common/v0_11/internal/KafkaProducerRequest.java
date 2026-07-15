/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.Iterator;
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
public final class KafkaProducerRequest {

  private final ProducerRecord<?, ?> record;
  @Nullable private final String clientId;
  @Nullable private final String bootstrapServers;
  @Nullable private final String clusterId;

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record, Producer<?, ?> producer, @Nullable String bootstrapServers) {
    return new KafkaProducerRequest(record, extractClientId(producer), bootstrapServers, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record, @Nullable String clientId, @Nullable String bootstrapServers) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      @Nullable String clusterId) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers, clusterId);
  }

  private KafkaProducerRequest(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      @Nullable String clusterId) {
    this.record = record;
    this.clientId = clientId;
    this.bootstrapServers = bootstrapServers;
    this.clusterId = clusterId;
  }

  public ProducerRecord<?, ?> getRecord() {
    return record;
  }

  @Nullable
  public String getClientId() {
    return clientId;
  }

  @Nullable
  public String getBootstrapServers() {
    return bootstrapServers;
  }

  @Nullable
  public String getClusterId() {
    return clusterId;
  }

  @Nullable
  private static String extractClientId(Producer<?, ?> producer) {
    try {
      Map<MetricName, ? extends Metric> metrics = producer.metrics();
      Iterator<MetricName> metricIterator = metrics.keySet().iterator();
      return metricIterator.hasNext() ? metricIterator.next().tags().get("client-id") : null;
    } catch (RuntimeException ignored) {
      // ExceptionHandlingTest uses a Producer that throws exception on every method call
      return null;
    }
  }
}
