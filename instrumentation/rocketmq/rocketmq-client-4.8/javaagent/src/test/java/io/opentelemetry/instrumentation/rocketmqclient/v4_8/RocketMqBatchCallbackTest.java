/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqBatchCallbackTest extends AbstractRocketMqBatchCallbackTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  MQProducer configureProducer(DefaultMQProducer producer) {
    return producer;
  }

  @Override
  boolean hasBatchCreateSpans() {
    return !Boolean.getBoolean("testBatchCreateSpansDisabled");
  }
}
