/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

/**
 * Helper for consumer-side instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class KafkaConsumerTelemetry {

  private final Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter;

  public KafkaConsumerTelemetry(
      Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter) {
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  // this getter is needed for the deprecated wrap() methods in KafkaTelemetry
  public Instrumenter<KafkaProcessRequest, Void> getConsumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  // this overload is needed for the deprecated wrap() methods in KafkaTelemetry
  public <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records, Consumer<K, V> consumer, Timer timer) {
    return buildAndFinishSpan(
        records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer), timer);
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
