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
  private static final VirtualField<ConsumerRecord<?, ?>, Context> recordContextField =
      VirtualField.find(ConsumerRecord.class, Context.class);
  private static final VirtualField<ConsumerRecord<?, ?>, String[]> recordConsumerInfoField =
      VirtualField.find(ConsumerRecord.class, String[].class);
  private static final VirtualField<ConsumerRecords<?, ?>, Context> recordsContextField =
      VirtualField.find(ConsumerRecords.class, Context.class);
  private static final VirtualField<ConsumerRecords<?, ?>, String[]> recordsConsumerInfoField =
      VirtualField.find(ConsumerRecords.class, String[].class);

  public static Context withoutLeakedProcessSpan(Context context) {
    if (!emitStableMessagingSemconv()) {
      return context;
    }

    Span currentSpan = Span.fromContext(context);
    if (currentSpan != context.get(PROCESS_SPAN_KEY)) {
      return context;
    }

    Span parentSpan = context.get(PROCESS_PARENT_SPAN_KEY);
    return context.with(parentSpan != null ? parentSpan : Span.getInvalid());
  }

  public static Context withProcessParentSpan(Context context, Context parentContext) {
    return context
        .with(PROCESS_SPAN_KEY, Span.fromContext(context))
        .with(PROCESS_PARENT_SPAN_KEY, Span.fromContext(parentContext));
  }

  public static Context withReceiveOperation(Context context) {
    return context.with(RECEIVE_OPERATION_KEY, true);
  }

  public static boolean hasReceiveOperation(Context context) {
    return Boolean.TRUE.equals(context.get(RECEIVE_OPERATION_KEY));
  }

  public static KafkaConsumerContext get(ConsumerRecord<?, ?> records) {
    Context receiveContext = recordContextField.get(records);
    String consumerGroup = null;
    String clientId = null;
    String[] consumerInfo = recordConsumerInfoField.get(records);
    if (consumerInfo != null) {
      consumerGroup = consumerInfo[0];
      clientId = consumerInfo[1];
    }
    return create(receiveContext, consumerGroup, clientId);
  }

  public static KafkaConsumerContext get(ConsumerRecords<?, ?> records) {
    Context receiveContext = recordsContextField.get(records);
    String consumerGroup = null;
    String clientId = null;
    String[] consumerInfo = recordsConsumerInfoField.get(records);
    if (consumerInfo != null) {
      consumerGroup = consumerInfo[0];
      clientId = consumerInfo[1];
    }
    return create(receiveContext, consumerGroup, clientId);
  }

  public static KafkaConsumerContext create(@Nullable Context context, Consumer<?, ?> consumer) {
    return create(context, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer));
  }

  public static KafkaConsumerContext create(
      @Nullable Context context, @Nullable String consumerGroup, @Nullable String clientId) {
    return KafkaConsumerContext.create(context, consumerGroup, clientId);
  }

  public static void set(ConsumerRecord<?, ?> record, KafkaConsumerContext consumerContext) {
    set(
        record,
        consumerContext.getContext(),
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId());
  }

  private static void set(
      ConsumerRecord<?, ?> record,
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId) {
    recordContextField.set(record, context);
    recordConsumerInfoField.set(record, new String[] {consumerGroup, clientId});
  }

  public static void set(ConsumerRecords<?, ?> records, KafkaConsumerContext consumerContext) {
    set(
        records,
        consumerContext.getContext(),
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId());
  }

  private static void set(
      ConsumerRecords<?, ?> records,
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId) {
    recordsContextField.set(records, context);
    recordsConsumerInfoField.set(records, new String[] {consumerGroup, clientId});
  }

  public static void copy(ConsumerRecord<?, ?> from, ConsumerRecord<?, ?> to) {
    recordContextField.set(to, recordContextField.get(from));
    recordConsumerInfoField.set(to, recordConsumerInfoField.get(from));
  }

  private KafkaConsumerContextUtil() {}
}
