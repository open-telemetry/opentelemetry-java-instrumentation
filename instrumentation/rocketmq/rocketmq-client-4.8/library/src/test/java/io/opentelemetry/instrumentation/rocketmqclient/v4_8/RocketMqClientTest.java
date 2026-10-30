/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class RocketMqClientTest extends AbstractRocketMqClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  MQProducer configureMqProducer(DefaultMQProducer producer) {
    return RocketMqTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedHeaders(asList("Test-Message-Header", "Test-Message-Int-Header"))
        .setCaptureExperimentalSpanAttributes(true)
        .build()
        .wrap(producer);
  }

  @Override
  void configureMqPushConsumer(DefaultMQPushConsumer consumer) {
    consumer
        .getDefaultMQPushConsumerImpl()
        .registerConsumeMessageHook(
            RocketMqTelemetry.builder(testing.getOpenTelemetry())
                .setCapturedHeaders(asList("Test-Message-Header", "Test-Message-Int-Header"))
                .setCaptureExperimentalSpanAttributes(true)
                .build()
                .createConsumeMessageHook());
  }

  @Override
  boolean hasBatchCreateSpans() {
    return false;
  }

  @Override
  boolean isJavaagent() {
    return false;
  }
}
