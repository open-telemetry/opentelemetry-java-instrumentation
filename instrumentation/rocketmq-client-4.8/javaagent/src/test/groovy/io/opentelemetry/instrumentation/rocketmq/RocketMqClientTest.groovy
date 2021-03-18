/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import io.opentelemetery.instrumentation.rocketmq.AbstractRocketMqClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import spock.lang.Ignore

@Ignore("https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2586")
class RocketMqClientTest extends AbstractRocketMqClientTest implements AgentTestTrait {

  @Override
  void configureMQProducer(DefaultMQProducer producer) {
  }

  @Override
  void configureMQPushConsumer(DefaultMQPushConsumer consumer) {
  }
}