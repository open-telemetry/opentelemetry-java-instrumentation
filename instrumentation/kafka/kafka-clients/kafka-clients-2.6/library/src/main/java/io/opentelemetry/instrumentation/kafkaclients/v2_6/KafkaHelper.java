/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaHeadersSetter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;

public final class KafkaHelper {
  private static final Logger logger = Logger.getLogger(KafkaHelper.class.getName());

  private static final TextMapSetter<Headers> SETTER = KafkaHeadersSetter.INSTANCE;

  private final TextMapPropagator propagator;
  private final Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter;
  private final Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter;
  private final boolean producerPropagationEnabled;

  KafkaHelper(
      TextMapPropagator propagator,
      Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter,
      Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter,
      boolean producerPropagationEnabled) {
    this.propagator = propagator;
    this.producerInstrumenter = producerInstrumenter;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
    this.producerPropagationEnabled = producerPropagationEnabled;
  }

  /**
   * Build and inject span into record.
   *
   * @param record the producer record to inject span info.
   */
  public <K, V> void buildAndInjectSpan(ProducerRecord<K, V> record, String clientId) {
    Context parentContext = Context.current();

    KafkaProducerRequest request = KafkaProducerRequest.create(record, clientId);
    if (!producerInstrumenter.shouldStart(parentContext, request)) {
      return;
    }

    Context context = producerInstrumenter.start(parentContext, request);
    if (producerPropagationEnabled) {
      try {
        propagator.inject(context, record.headers(), SETTER);
      } catch (Throwable t) {
        // it can happen if headers are read only (when record is sent second time)
        logger.log(WARNING, "failed to inject span context. sending record second time?", t);
      }
    }
    producerInstrumenter.end(context, request, null, null);
  }

  public <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records, String consumerGroup, String clientId, Timer timer) {
    if (records.isEmpty()) {
      return null;
    }
    Context parentContext = Context.current();
    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumerGroup, clientId);
    Context context = null;
    if (consumerReceiveInstrumenter.shouldStart(parentContext, request)) {
      context =
          InstrumenterUtil.startAndEnd(
              consumerReceiveInstrumenter,
              parentContext,
              request,
              null,
              null,
              timer.startTime(),
              timer.now());
    }

    // we're returning the context of the receive span so that process spans can use it as
    // parent context even though the span has ended
    // this is the suggested behavior according to the spec batch receive scenario:
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-spans.md#batch-receiving
    return context;
  }

  public <K, V> ConsumerRecords<K, V> addTracing(
      ConsumerRecords<K, V> consumerRecords, KafkaConsumerContext consumerContext) {
    if (consumerRecords.isEmpty()) {
      return consumerRecords;
    }

    Map<TopicPartition, List<ConsumerRecord<K, V>>> records = new LinkedHashMap<>();
    for (TopicPartition partition : consumerRecords.partitions()) {
      List<ConsumerRecord<K, V>> list = consumerRecords.records(partition);
      if (list != null && !list.isEmpty()) {
        list = TracingList.wrap(list, consumerProcessInstrumenter, () -> true, consumerContext);
      }
      records.put(partition, list);
    }
    return new ConsumerRecords<>(records);
  }
}
