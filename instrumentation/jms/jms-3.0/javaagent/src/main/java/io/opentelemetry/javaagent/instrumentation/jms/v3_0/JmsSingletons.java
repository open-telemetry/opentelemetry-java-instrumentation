/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;

public class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-3.0";

  private static final Instrumenter<MessageWithDestination, Void> producerInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveTelemetryEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());

    producerInstrumenter = factory.createProducerInstrumenter();
    consumerReceiveInstrumenter = factory.createConsumerReceiveInstrumenter();
    consumerProcessInstrumenter = factory.createConsumerProcessInstrumenter(false);
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return producerInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter() {
    return consumerReceiveInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  private JmsSingletons() {}
}
