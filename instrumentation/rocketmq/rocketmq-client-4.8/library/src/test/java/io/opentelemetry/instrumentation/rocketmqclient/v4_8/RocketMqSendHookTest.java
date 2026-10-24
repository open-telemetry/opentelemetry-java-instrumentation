/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqSendHookTest extends AbstractRocketMqSendHookTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  MQProducer configureProducer(DefaultMQProducer producer) {
    return RocketMqTelemetry.create(testing.getOpenTelemetry()).wrap(producer);
  }

  @Override
  boolean hasBatchCreateSpans() {
    return false;
  }
}
