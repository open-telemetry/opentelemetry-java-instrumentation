/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

public class RocketMqSingletons {

  private static final Instrumenter<PublishingMessageImpl, SendReceiptImpl> producerInstrumenter;
  private static final Instrumenter<RocketMqReceiveRequest, List<MessageView>>
      consumerReceiveInstrumenter;
  private static final Instrumenter<RocketMqReceiveRequest, List<MessageView>>
      simpleConsumerReceiveInstrumenter;
  private static final Instrumenter<MessageView, ConsumeResult> consumerProcessInstrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    IncludeExclude messagingHeaders = ExperimentalConfig.get().getMessagingHeaders();
    boolean receiveInstrumentationEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

    producerInstrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(openTelemetry, messagingHeaders);
    consumerReceiveInstrumenter =
        RocketMqInstrumenterFactory.createConsumerReceiveInstrumenter(
            openTelemetry, messagingHeaders, receiveInstrumentationEnabled);
    simpleConsumerReceiveInstrumenter =
        RocketMqInstrumenterFactory.createConsumerReceiveInstrumenter(
            openTelemetry, messagingHeaders, true);
    consumerProcessInstrumenter =
        RocketMqInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, messagingHeaders, receiveInstrumentationEnabled);
  }

  public static Instrumenter<PublishingMessageImpl, SendReceiptImpl> producerInstrumenter() {
    return producerInstrumenter;
  }

  public static Instrumenter<RocketMqReceiveRequest, List<MessageView>>
      consumerReceiveInstrumenter() {
    return consumerReceiveInstrumenter;
  }

  public static Instrumenter<RocketMqReceiveRequest, List<MessageView>>
      simpleConsumerReceiveInstrumenter() {
    return simpleConsumerReceiveInstrumenter;
  }

  public static Instrumenter<MessageView, ConsumeResult> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  private RocketMqSingletons() {}
}
