/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

class ConsumerConfigUtil {
  static void addErrorHandler(ConcurrentKafkaListenerContainerFactory<String, String> factory) {
    factory.setCommonErrorHandler(
        new CommonErrorHandler() {

          @Override
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
