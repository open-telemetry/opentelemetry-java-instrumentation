/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerContextUtil {
  private static final VirtualField<ConsumerRecord<?, ?>, Context> recordContextField =
      VirtualField.find(ConsumerRecord.class, Context.class);
  private static final VirtualField<ConsumerRecord<?, ?>, Consumer<?, ?>> recordConsumerField =
      VirtualField.find(ConsumerRecord.class, Consumer.class);
  private static final VirtualField<ConsumerRecords<?, ?>, Context> recordsContextField =
      VirtualField.find(ConsumerRecords.class, Context.class);
  private static final VirtualField<ConsumerRecords<?, ?>, Consumer<?, ?>> recordsConsumerField =
      VirtualField.find(ConsumerRecords.class, Consumer.class);

  public static KafkaConsumerContext get(ConsumerRecord<?, ?> records) {
    Context receiveContext = recordContextField.get(records);
    Consumer<?, ?> consumer = recordConsumerField.get(records);
    return KafkaConsumerContext.create(receiveContext, consumer);
  }

  public static KafkaConsumerContext get(ConsumerRecords<?, ?> records) {
    Context receiveContext = recordsContextField.get(records);
    Consumer<?, ?> consumer = recordsConsumerField.get(records);
    return KafkaConsumerContext.create(receiveContext, consumer);
  }

  public static void set(ConsumerRecord<?, ?> record, Context context, Consumer<?, ?> consumer) {
    recordContextField.set(record, context);
    recordConsumerField.set(record, consumer);
  }

  public static void set(ConsumerRecord<?, ?> record, KafkaConsumerContext consumerContext) {
    set(record, consumerContext.getContext(), consumerContext.getConsumer());
  }

  public static void set(ConsumerRecords<?, ?> records, Context context, Consumer<?, ?> consumer) {
    recordsContextField.set(records, context);
    recordsConsumerField.set(records, consumer);
  }

  private KafkaConsumerContextUtil() {}
}
