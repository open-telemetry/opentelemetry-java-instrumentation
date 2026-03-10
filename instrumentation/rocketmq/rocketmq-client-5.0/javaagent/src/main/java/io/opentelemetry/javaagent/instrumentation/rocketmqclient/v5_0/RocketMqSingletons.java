/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

public final class RocketMqSingletons {

  private static final Instrumenter<PublishingMessageImpl, SendReceiptImpl> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<ReceiveMessageRequest, List<MessageView>>
      CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<MessageView, ConsumeResult> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    List<String> messagingHeaders = ExperimentalConfig.get().getMessagingHeaders();
    boolean receiveInstrumentationEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

    PRODUCER_INSTRUMENTER =
        RocketMqInstrumenterFactory.createProducerInstrumenter(openTelemetry, messagingHeaders);
    CONSUMER_RECEIVE_INSTRUMENTER =
        RocketMqInstrumenterFactory.createConsumerReceiveInstrumenter(
            openTelemetry, messagingHeaders, receiveInstrumentationEnabled);
    CONSUMER_PROCESS_INSTRUMENTER =
        RocketMqInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, messagingHeaders, receiveInstrumentationEnabled);
  }

  public static Instrumenter<PublishingMessageImpl, SendReceiptImpl> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ReceiveMessageRequest, List<MessageView>>
      consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<MessageView, ConsumeResult> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  private RocketMqSingletons() {}
}
