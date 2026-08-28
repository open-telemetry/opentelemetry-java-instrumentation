/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
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
  void configureMqProducer(DefaultMQProducer producer) {
    producer
        .getDefaultMQProducerImpl()
        .registerSendMessageHook(
            RocketMqTelemetry.builder(testing.getOpenTelemetry())
                .setHeaders(
                    IncludeExclude.builder()
                        .setIncluded("Test-Message-*")
                        .setExcluded("*-Excluded-Header")
                        .build())
                .setCaptureExperimentalSpanAttributes(true)
                .build()
                .createSendMessageHook());
  }

  @Override
  void configureMqPushConsumer(DefaultMQPushConsumer consumer) {
    consumer
        .getDefaultMQPushConsumerImpl()
        .registerConsumeMessageHook(
            RocketMqTelemetry.builder(testing.getOpenTelemetry())
                .setHeaders(
                    IncludeExclude.builder()
                        .setIncluded("Test-Message-*")
                        .setExcluded("*-Excluded-Header")
                        .build())
                .setCaptureExperimentalSpanAttributes(true)
                .build()
                .createConsumeMessageHook());
  }
}
