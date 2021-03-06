/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import base.BaseConf
import io.opentelemetery.instrumentation.rocketmq.AbstractRocketMqClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.rocketmq.test.listener.rmq.order.RMQOrderListener

class RocketMqClientTest extends AbstractRocketMqClientTest implements AgentTestTrait {

  @Override
  void configureMQProducer() {
    producer=BaseConf.getProducer(BaseConf.nsAddr)
  }

  @Override
  void configureMQPushConsumer() {
    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", new RMQOrderListener())
    consumer.setConsumeMessageBatchMaxSize(2)
  }

}