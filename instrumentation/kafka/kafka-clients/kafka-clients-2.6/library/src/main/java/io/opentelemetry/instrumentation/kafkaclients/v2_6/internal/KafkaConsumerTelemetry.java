/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static java.util.Collections.emptyMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingList;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * Helper for consumer-side instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class KafkaConsumerTelemetry {
  private static final MethodHandle NEXT_OFFSETS;
  private static final MethodHandle NEW_CONSUMER_RECORDS;

  static {
    MethodHandle nextOffsets;
    MethodHandle newConsumerRecords;

    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      nextOffsets =
          lookup.findVirtual(
              ConsumerRecords.class, "nextOffsets", MethodType.methodType(Map.class));
      newConsumerRecords =
          lookup.findConstructor(
              ConsumerRecords.class, MethodType.methodType(void.class, Map.class, Map.class));
    } catch (IllegalAccessException | NoSuchMethodException ignored) {
      nextOffsets = null;
      newConsumerRecords = null;
    }

    NEXT_OFFSETS = nextOffsets;
    NEW_CONSUMER_RECORDS = newConsumerRecords;
  }

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
    return newConsumerRecords(records, consumerRecords);
  }

  @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
  private static <K, V> ConsumerRecords<K, V> newConsumerRecords(
      Map<TopicPartition, List<ConsumerRecord<K, V>>> records,
      ConsumerRecords<K, V> consumerRecords) {
    if (NEXT_OFFSETS != null && NEW_CONSUMER_RECORDS != null) {
      try {
        Map<TopicPartition, OffsetAndMetadata> nextOffsets =
            (Map<TopicPartition, OffsetAndMetadata>) NEXT_OFFSETS.invoke(consumerRecords);
        return (ConsumerRecords<K, V>) NEW_CONSUMER_RECORDS.invoke(records, nextOffsets);
      } catch (Throwable t) {
        sneakyThrow(t);
      }
    }
    return new ConsumerRecords<>(records);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"}) // fine
  private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  @Nullable
  public <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records, Consumer<K, V> consumer, Timer timer) {
    return buildAndFinishSpan(
        records,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getClusterId(consumer),
        timer);
  }

  @Nullable
  <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    return buildAndFinishSpan(records, consumerGroup, clientId, clusterId, null);
  }

  @Nullable
  private <K, V> Context buildAndFinishSpan(
      ConsumerRecords<K, V> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId,
      @Nullable Timer timer) {
    if (records.isEmpty()) {
      return null;
    }
    Context parentContext = KafkaConsumerContextUtil.withoutLeakedProcessSpan(Context.current());
    KafkaReceiveRequest request =
        KafkaReceiveRequest.create(records, consumerGroup, clientId, clusterId);
    boolean receiveOperationStarted = false;
    if (consumerReceiveInstrumenter.shouldStart(parentContext, request)) {
      if (timer == null) {
        // The interceptor runs after poll, so let the SDK time an immediate span, not poll
        // duration.
        Context receiveContext = consumerReceiveInstrumenter.start(parentContext, request);
        consumerReceiveInstrumenter.end(receiveContext, request, null, null);
      } else {
        InstrumenterUtil.startAndEnd(
            consumerReceiveInstrumenter,
            parentContext,
            request,
            null,
            null,
            timer.startTime(),
            timer.now());
      }
      receiveOperationStarted = true;
    }

    return KafkaConsumerContextUtil.withReceiveOperation(parentContext, receiveOperationStarted);
  }

  public <K, V> void buildAndFinishErrorSpan(
      Consumer<K, V> consumer, Timer timer, Throwable error) {
    Context parentContext = KafkaConsumerContextUtil.withoutLeakedProcessSpan(Context.current());
    ConsumerRecords<K, V> records = new ConsumerRecords<>(emptyMap());
    KafkaReceiveRequest request =
        KafkaReceiveRequest.create(
            records,
            KafkaUtil.getConsumerGroup(consumer),
            KafkaUtil.getClientId(consumer),
            KafkaUtil.getClusterId(consumer));
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
