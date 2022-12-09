/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());

    PRODUCER_INSTRUMENTER = factory.buildProducerInstrumenter();
    CONSUMER_INSTRUMENTER = factory.buildConsumerInstrumenter();
    LISTENER_INSTRUMENTER = factory.buildListenerInstrumenter();
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerInstrumenter() {
    return CONSUMER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private JmsSingletons() {}
}
