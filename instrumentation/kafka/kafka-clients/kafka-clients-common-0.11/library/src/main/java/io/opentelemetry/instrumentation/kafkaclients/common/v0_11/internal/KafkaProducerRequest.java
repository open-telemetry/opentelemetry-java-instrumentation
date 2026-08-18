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
  private final boolean spanContextPropagated;
  @Nullable private final String clusterId;
  // Retained so onEnd() can retry the cluster-id lookup when it was null at send() time.
  @Nullable private final Producer<?, ?> producer;

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record, Producer<?, ?> producer, @Nullable String bootstrapServers) {
    return new KafkaProducerRequest(
        record,
        extractClientId(producer),
        bootstrapServers,
        true,
        KafkaUtil.getClusterId(producer),
        producer);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      Producer<?, ?> producer,
      @Nullable String bootstrapServers,
      boolean spanContextPropagated) {
    return new KafkaProducerRequest(
        record,
        extractClientId(producer),
        bootstrapServers,
        spanContextPropagated,
        KafkaUtil.getClusterId(producer),
        producer);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record, @Nullable String clientId, @Nullable String bootstrapServers) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers, true, null, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      boolean spanContextPropagated) {
    return new KafkaProducerRequest(
        record, clientId, bootstrapServers, spanContextPropagated, null, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      @Nullable String clusterId) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers, true, clusterId, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      @Nullable String clusterId,
      @Nullable Producer<?, ?> producer) {
    return new KafkaProducerRequest(record, clientId, bootstrapServers, true, clusterId, producer);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      boolean spanContextPropagated,
      @Nullable String clusterId) {
    return new KafkaProducerRequest(
        record, clientId, bootstrapServers, spanContextPropagated, clusterId, null);
  }

  public static KafkaProducerRequest create(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      boolean spanContextPropagated,
      @Nullable String clusterId,
      @Nullable Producer<?, ?> producer) {
    return new KafkaProducerRequest(
        record, clientId, bootstrapServers, spanContextPropagated, clusterId, producer);
  }

  private KafkaProducerRequest(
      ProducerRecord<?, ?> record,
      @Nullable String clientId,
      @Nullable String bootstrapServers,
      boolean spanContextPropagated,
      @Nullable String clusterId,
      @Nullable Producer<?, ?> producer) {
    this.record = record;
    this.clientId = clientId;
    this.bootstrapServers = bootstrapServers;
    this.spanContextPropagated = spanContextPropagated;
    this.clusterId = clusterId;
    this.producer = producer;
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

  public boolean isSpanContextPropagated() {
    return spanContextPropagated;
  }

  @Nullable
  public String getClusterId() {
    return clusterId;
  }

  @Nullable
  Producer<?, ?> getProducer() {
    return producer;
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
