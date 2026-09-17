/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;

public class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> producerInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void>
      consumerProcessInstrumenterWithConsumedMessages;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveTelemetryEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());

    producerInstrumenter = factory.createProducerInstrumenter();
    consumerReceiveInstrumenter = factory.createConsumerReceiveInstrumenter();
    consumerProcessInstrumenter = factory.createConsumerProcessInstrumenter(false, false);
    consumerProcessInstrumenterWithConsumedMessages =
        emitStableMessagingSemconv()
            ? factory.createConsumerProcessInstrumenter(false, true)
            : consumerProcessInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return producerInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter() {
    return consumerReceiveInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter(
      boolean consumedMessagesRecorded) {
    return consumedMessagesRecorded
        ? consumerProcessInstrumenter
        : consumerProcessInstrumenterWithConsumedMessages;
  }

  private JmsSingletons() {}
}
