/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

class ConsumerConfigUtil {
  static void addErrorHandler(ConcurrentKafkaListenerContainerFactory<String, String> factory) {
    factory.setCommonErrorHandler(
        new CommonErrorHandler() {

          @Override
          public void handleRecord(
              Exception thrownException,
              ConsumerRecord<?, ?> record,
              Consumer<?, ?> consumer,
              MessageListenerContainer container) {
            GlobalTraceUtil.runWithSpan("handle exception", () -> {});
            // 2.8 cannot return "not handled"; seek and propagate the error to retry the record.
            consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
            throw new KafkaException("Retry failed record", thrownException);
          }

          // Replaces handleRecord in Spring Kafka 2.9; compiled against the 2.8 floor.
          @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod", "UnusedVariable"})
          public boolean handleOne(
              Exception thrownException,
              ConsumerRecord<?, ?> record,
              Consumer<?, ?> consumer,
              MessageListenerContainer container) {
            GlobalTraceUtil.runWithSpan("handle exception", () -> {});
            return false;
          }
        });
  }

  private ConsumerConfigUtil() {}
}
