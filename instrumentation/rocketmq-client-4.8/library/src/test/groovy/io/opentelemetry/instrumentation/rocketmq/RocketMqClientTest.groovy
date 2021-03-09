/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import io.opentelemetery.instrumentation.rocketmq.AbstractRocketMqClientTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer

class RocketMqClientTest extends AbstractRocketMqClientTest implements LibraryTestTrait {

  @Override
  void configureMQProducer(DefaultMQProducer producer) {
    producer.getDefaultMQProducerImpl().registerSendMessageHook(RocketMqTracing.create(getOpenTelemetry()).newTracingSendMessageHook())
  }

  @Override
  void configureMQPushConsumer(DefaultMQPushConsumer consumer) {
    consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(RocketMqTracing.create(getOpenTelemetry()).newTracingConsumeMessageHook())
  }
}