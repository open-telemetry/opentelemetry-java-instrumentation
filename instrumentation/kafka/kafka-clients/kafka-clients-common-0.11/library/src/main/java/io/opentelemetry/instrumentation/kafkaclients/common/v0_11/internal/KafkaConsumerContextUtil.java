/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerContextUtil {
  private static final ContextKey<Span> PROCESS_SPAN_KEY =
      ContextKey.named("opentelemetry-kafka-process-span");
  private static final ContextKey<Span> PROCESS_PARENT_SPAN_KEY =
      ContextKey.named("opentelemetry-kafka-process-parent-span");
  private static final ContextKey<Boolean> RECEIVE_OPERATION_KEY =
      ContextKey.named("opentelemetry-kafka-receive-operation");
  // these fields can be used for multiple instrumentations because of that we don't use a helper
  // class as field type
  private static final VirtualField<ConsumerRecord<?, ?>, Context> RECORD_CONTEXT =
      VirtualField.find(ConsumerRecord.class, Context.class);
  private static final VirtualField<ConsumerRecord<?, ?>, String[]> RECORD_CONSUMER_INFO =
      VirtualField.find(ConsumerRecord.class, String[].class);
  private static final VirtualField<ConsumerRecords<?, ?>, Context> RECORDS_CONTEXT =
      VirtualField.find(ConsumerRecords.class, Context.class);
  private static final VirtualField<ConsumerRecords<?, ?>, String[]> RECORDS_CONSUMER_INFO =
      VirtualField.find(ConsumerRecords.class, String[].class);
  private static final VirtualField<ConsumerRecord<?, ?>, Boolean> RECORD_COUNTED =
      VirtualField.find(ConsumerRecord.class, Boolean.class);

  public static Context withoutLeakedProcessSpan(Context context) {
    if (!emitStableMessagingSemconv()) {
      return context;
    }

    Span processSpan = context.get(PROCESS_SPAN_KEY);
    if (processSpan == null) {
      return context;
    }

    Span currentSpan = Span.fromContext(context);
    if (currentSpan != processSpan) {
      return context;
    }

    Span parentSpan = context.get(PROCESS_PARENT_SPAN_KEY);
    Context restored = context.with(parentSpan != null ? parentSpan : Span.getInvalid());
    return restored.with(RECEIVE_OPERATION_KEY, false);
  }

  public static Context withProcessParentSpan(Context context, Context parentContext) {
    return context
        .with(PROCESS_SPAN_KEY, Span.fromContext(context))
        .with(PROCESS_PARENT_SPAN_KEY, Span.fromContext(parentContext));
  }

  public static Context withReceiveOperation(Context context, boolean receiveOperation) {
    return context.with(RECEIVE_OPERATION_KEY, receiveOperation);
  }

  public static boolean hasReceiveOperation(Context context) {
    return Boolean.TRUE.equals(context.get(RECEIVE_OPERATION_KEY));
  }

  /**
   * Returns {@code true} the first time the given record is seen, and {@code false} afterwards, so
   * that operations that observe the same record do not count it twice.
   */
  public static boolean markConsumedMessageCounted(ConsumerRecord<?, ?> record) {
    if (Boolean.TRUE.equals(RECORD_COUNTED.get(record))) {
      return false;
    }
    RECORD_COUNTED.set(record, true);
    return true;
  }

  public static KafkaConsumerContext get(ConsumerRecord<?, ?> records) {
    Context receiveContext = RECORD_CONTEXT.get(records);
    String consumerGroup = null;
    String clientId = null;
    String clusterId = null;
    String[] consumerInfo = RECORD_CONSUMER_INFO.get(records);
    if (consumerInfo != null) {
      consumerGroup = consumerInfo[0];
      clientId = consumerInfo[1];
      clusterId = consumerInfo.length > 2 ? consumerInfo[2] : null;
    }
    return create(receiveContext, consumerGroup, clientId, clusterId);
  }

  public static KafkaConsumerContext get(ConsumerRecords<?, ?> records) {
    Context receiveContext = RECORDS_CONTEXT.get(records);
    String consumerGroup = null;
    String clientId = null;
    String clusterId = null;
    String[] consumerInfo = RECORDS_CONSUMER_INFO.get(records);
    if (consumerInfo != null) {
      consumerGroup = consumerInfo[0];
      clientId = consumerInfo[1];
      clusterId = consumerInfo.length > 2 ? consumerInfo[2] : null;
    }
    return create(receiveContext, consumerGroup, clientId, clusterId);
  }

  public static KafkaConsumerContext create(@Nullable Context context, Consumer<?, ?> consumer) {
    return create(
        context,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getClusterId(consumer));
  }

  public static KafkaConsumerContext create(
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    return KafkaConsumerContext.create(context, consumerGroup, clientId, clusterId);
  }

  public static void set(ConsumerRecord<?, ?> record, KafkaConsumerContext consumerContext) {
    set(
        record,
        consumerContext.getContext(),
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getClusterId());
  }

  private static void set(
      ConsumerRecord<?, ?> record,
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    RECORD_CONTEXT.set(record, context);
    RECORD_CONSUMER_INFO.set(record, new String[] {consumerGroup, clientId, clusterId});
  }

  public static void set(ConsumerRecords<?, ?> records, KafkaConsumerContext consumerContext) {
    set(
        records,
        consumerContext.getContext(),
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getClusterId());
  }

  private static void set(
      ConsumerRecords<?, ?> records,
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    RECORDS_CONTEXT.set(records, context);
    RECORDS_CONSUMER_INFO.set(records, new String[] {consumerGroup, clientId, clusterId});
  }

  public static void copy(ConsumerRecord<?, ?> from, ConsumerRecord<?, ?> to) {
    RECORD_CONTEXT.set(to, RECORD_CONTEXT.get(from));
    RECORD_CONSUMER_INFO.set(to, RECORD_CONSUMER_INFO.get(from));
  }

  private KafkaConsumerContextUtil() {}
}
