/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerExperimentalAttributesExtractor
    implements AttributesExtractor<ConsumerRecord<?, ?>, Void> {

  private static final AttributeKey<Long> KAFKA_OFFSET = longKey("kafka.offset");
  private static final AttributeKey<Long> KAFKA_RECORD_QUEUE_TIME_MS =
      longKey("kafka.record.queue_time_ms");

  private static final boolean ENABLED =
      Config.get().getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false);

  public static boolean isEnabled() {
    return ENABLED;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ConsumerRecord<?, ?> consumerRecord) {
    set(attributes, KAFKA_OFFSET, consumerRecord.offset());

    // don't record a duration if the message was sent from an old Kafka client
    if (consumerRecord.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
      long produceTime = consumerRecord.timestamp();
      // this attribute shows how much time elapsed between the producer and the consumer of this
      // message, which can be helpful for identifying queue bottlenecks
      set(
          attributes,
          KAFKA_RECORD_QUEUE_TIME_MS,
          Math.max(0L, System.currentTimeMillis() - produceTime));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ConsumerRecord<?, ?> consumerRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
