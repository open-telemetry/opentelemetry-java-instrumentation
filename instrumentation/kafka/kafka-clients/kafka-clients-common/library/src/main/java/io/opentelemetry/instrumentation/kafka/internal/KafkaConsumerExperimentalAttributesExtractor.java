/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

final class KafkaConsumerExperimentalAttributesExtractor
    implements AttributesExtractor<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void> {

  private static final AttributeKey<Long> KAFKA_RECORD_QUEUE_TIME_MS =
      longKey("kafka.record.queue_time_ms");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {

    // don't record a duration if the message was sent from an old Kafka client
    if (consumerAndRecord.record().timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
      long produceTime = consumerAndRecord.record().timestamp();
      // this attribute shows how much time elapsed between the producer and the consumer of this
      // message, which can be helpful for identifying queue bottlenecks
      attributes.put(
          KAFKA_RECORD_QUEUE_TIME_MS, Math.max(0L, System.currentTimeMillis() - produceTime));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
