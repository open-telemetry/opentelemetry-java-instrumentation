/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqClientTest extends AbstractRocketMqClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  @SuppressWarnings("deprecation")
  // testing instrumentation of deprecated class
  void configureMqProducer(DefaultMQProducer producer) {
    producer
        .getDefaultMQProducerImpl()
        .registerSendMessageHook(
            RocketMqTelemetry.builder(testing.getOpenTelemetry())
                .setCapturedHeaders(singletonList("test-message-header"))
                .setCaptureExperimentalSpanAttributes(true)
                .build()
                .newTracingSendMessageHook());
  }

  @Override
  @SuppressWarnings("deprecation")
  // testing instrumentation of deprecated class
  void configureMqPushConsumer(DefaultMQPushConsumer consumer) {
    consumer
        .getDefaultMQPushConsumerImpl()
        .registerConsumeMessageHook(
            RocketMqTelemetry.builder(testing.getOpenTelemetry())
                .setCapturedHeaders(singletonList("test-message-header"))
                .setCaptureExperimentalSpanAttributes(true)
                .build()
                .newTracingConsumeMessageHook());
  }
}
