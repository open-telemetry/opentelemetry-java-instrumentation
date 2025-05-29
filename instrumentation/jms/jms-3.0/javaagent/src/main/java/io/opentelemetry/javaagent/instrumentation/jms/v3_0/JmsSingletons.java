/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-3.0";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());

    PRODUCER_INSTRUMENTER = factory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = factory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = factory.createConsumerProcessInstrumenter(false);
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  private JmsSingletons() {}
}
