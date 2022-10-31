/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8


import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer

class RocketMqClientTest extends AbstractRocketMqClientTest implements AgentTestTrait {

  @Override
  void configureMQProducer(DefaultMQProducer producer) {
  }

  @Override
  void configureMQPushConsumer(DefaultMQPushConsumer consumer) {
  }
}