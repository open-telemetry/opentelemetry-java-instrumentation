/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_CONSUMER_NAME;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_PRODUCER_NAME;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_QUANTILE;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_RESPONSE_STATUS;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_SUBSCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.ATTRIBUTE_TOPIC;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.CONSUMER_METRICS_PREFIX;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.PRODUCER_METRICS_PREFIX;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarMetricsUtil.SCOPE_NAME;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.pulsar.client.impl.ConsumerImpl;
import org.apache.pulsar.client.impl.ProducerImpl;

public class PulsarMetricsRegistry {

  private final Set<ProducerImpl<?>> producers = Collections.synchronizedSet(new HashSet<>());
  private final Set<ConsumerImpl<?>> consumers = Collections.synchronizedSet(new HashSet<>());
  private final Meter meter =
      GlobalOpenTelemetry.getMeterProvider().meterBuilder(SCOPE_NAME).build();

  public void init() {

    /* =========================== Producer Metrics =========================== */

    // pulsar.client.producer.message.sent.size
    meter
        .gaugeBuilder(PRODUCER_METRICS_PREFIX + "message.send.size")
        .setUnit("bytes")
        .setDescription("Counts the size of sent messages")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ProducerImpl<?> producer : PulsarMetricsRegistry.this.producers) {
                measurement.record(
                    producer.getStats().getTotalBytesSent(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName()));
              }
            });

    // pulsar.client.producer.message.sent.count
    meter
        .gaugeBuilder(PRODUCER_METRICS_PREFIX + "message.send.count")
        .setUnit("messages")
        .setDescription("Counts the number of sent messages")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ProducerImpl<?> producer : PulsarMetricsRegistry.this.producers) {
                measurement.record(
                    producer.getStats().getTotalMsgsSent(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "success"));
                measurement.record(
                    producer.getStats().getTotalSendFailed(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "failure"));
              }
            });

    // pulsar.client.producer.message.sent.duration
    meter
        .gaugeBuilder(PRODUCER_METRICS_PREFIX + "message.send.duration")
        .setUnit("ms")
        .setDescription("The duration of sent messages")
        .buildWithCallback(
            measurement -> {
              for (ProducerImpl<?> producer : PulsarMetricsRegistry.this.producers) {
                measurement.record(
                    producer.getStats().getSendLatencyMillis50pct(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "0.5"));
                measurement.record(
                    producer.getStats().getSendLatencyMillis75pct(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "0.75"));
                measurement.record(
                    producer.getStats().getSendLatencyMillis95pct(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "0.95"));
                measurement.record(
                    producer.getStats().getSendLatencyMillis99pct(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "0.99"));
                measurement.record(
                    producer.getStats().getSendLatencyMillis999pct(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "0.999"));
                measurement.record(
                    producer.getStats().getSendLatencyMillisMax(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        producer.getTopic(),
                        ATTRIBUTE_PRODUCER_NAME,
                        producer.getProducerName(),
                        ATTRIBUTE_QUANTILE,
                        "max"));
              }
            });

    /* =========================== Consumer Metrics =========================== */

    // pulsar.client.consumer.message.received.size
    meter
        .gaugeBuilder(CONSUMER_METRICS_PREFIX + "message.received.size")
        .setUnit("bytes")
        .setDescription("Counts the size of received messages")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ConsumerImpl<?> consumer : PulsarMetricsRegistry.this.consumers) {
                measurement.record(
                    consumer.getStats().getTotalBytesReceived(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName()));
              }
            });

    // pulsar.client.consumer.message.received.count
    meter
        .gaugeBuilder(CONSUMER_METRICS_PREFIX + "message.received.count")
        .setUnit("messages")
        .setDescription("Counts the number of received messages")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ConsumerImpl<?> consumer : PulsarMetricsRegistry.this.consumers) {
                measurement.record(
                    consumer.getStats().getTotalMsgsReceived(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "success"));
                measurement.record(
                    consumer.getStats().getTotalReceivedFailed(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "failure"));
              }
            });

    // pulsar.client.consumer.acks.sent.count
    meter
        .gaugeBuilder(CONSUMER_METRICS_PREFIX + "message.ack.count")
        .setUnit("acks")
        .setDescription("Counts the number of sent message acknowledgements")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ConsumerImpl<?> consumer : PulsarMetricsRegistry.this.consumers) {
                measurement.record(
                    consumer.getStats().getTotalAcksSent(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "success"));
                measurement.record(
                    consumer.getStats().getTotalAcksFailed(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName(),
                        ATTRIBUTE_RESPONSE_STATUS,
                        "failure"));
              }
            });

    // pulsar.client.consumer.receiver.queue.usage
    meter
        .gaugeBuilder(CONSUMER_METRICS_PREFIX + "receiver.queue.usage")
        .setUnit("messages")
        .setDescription("Number of the messages in the receiver queue")
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              for (ConsumerImpl<?> consumer : PulsarMetricsRegistry.this.consumers) {
                measurement.record(
                    consumer.getTotalIncomingMessages(),
                    Attributes.of(
                        ATTRIBUTE_TOPIC,
                        consumer.getTopic(),
                        ATTRIBUTE_SUBSCRIPTION,
                        consumer.getSubscription(),
                        ATTRIBUTE_CONSUMER_NAME,
                        consumer.getConsumerName()));
              }
            });

    // TODO Add receiver queue limit metrics after the value is exposed by Pulsar Client.
  }

  public void registerProducer(ProducerImpl<?> producer) {
    producers.add(producer);
  }

  public void registerConsumer(ConsumerImpl<?> consumer) {
    consumers.add(consumer);
  }

  public void deleteProducer(ProducerImpl<?> producer) {
    producers.remove(producer);
  }

  public void deleteConsumer(ConsumerImpl<?> consumer) {
    consumers.remove(consumer);
  }

  public int getProducerSize() {
    return producers.size();
  }

  public int getConsumerSize() {
    return consumers.size();
  }

  public static class PulsarMetricsRegistryDisabled extends PulsarMetricsRegistry {

    public static final PulsarMetricsRegistry INSTANCE = new PulsarMetricsRegistryDisabled();

    @Override
    public void init() {}

    @Override
    public void registerProducer(ProducerImpl<?> producer) {}

    @Override
    public void registerConsumer(ConsumerImpl<?> consumer) {}

    @Override
    public void deleteProducer(ProducerImpl<?> producer) {}

    @Override
    public void deleteConsumer(ConsumerImpl<?> consumer) {}

    @Override
    public int getProducerSize() {
      return 0;
    }

    @Override
    public int getConsumerSize() {
      return 0;
    }
  }
}
