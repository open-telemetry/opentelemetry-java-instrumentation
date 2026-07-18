/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyMap;

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
import javax.annotation.Nullable;
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

  @Nullable
  public <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records, Consumer<K, V> consumer, Timer timer) {
    return buildAndFinishSpan(
        records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer), timer);
  }

  @Nullable
  public <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      Timer timer) {
    if (records.isEmpty()) {
      return null;
    }
    Context parentContext = Context.current();
    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumerGroup, clientId);
    Context receiveContext = null;
    if (consumerReceiveInstrumenter.shouldStart(parentContext, request)) {
      receiveContext =
          InstrumenterUtil.startAndEnd(
              consumerReceiveInstrumenter,
              parentContext,
              request,
              null,
              null,
              timer.startTime(),
              timer.now());
    }

    return emitStableMessagingSemconv() ? parentContext : receiveContext;
  }

  public <K, V> void buildAndFinishErrorSpan(
      Consumer<K, V> consumer, Timer timer, Throwable error) {
    Context parentContext = Context.current();
    ConsumerRecords<K, V> records = new ConsumerRecords<>(emptyMap());
    KafkaReceiveRequest request =
        KafkaReceiveRequest.create(
            records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer));
    if (consumerReceiveInstrumenter.shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          consumerReceiveInstrumenter,
          parentContext,
          request,
          null,
          error,
          timer.startTime(),
          timer.now());
    }
  }
}
