/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

public final class RocketMqSingletons {

  private static final Instrumenter<PublishingMessageImpl, SendReceiptImpl> PRODUCER_INSTRUMENTER;

  static {
    PRODUCER_INSTRUMENTER =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            GlobalOpenTelemetry.get(), ExperimentalConfig.get().getMessagingHeaders());
  }

  public static Instrumenter<PublishingMessageImpl, SendReceiptImpl> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  private RocketMqSingletons() {}
}
