/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

/** Entrypoint for tracing RocketMq producers or consumers. */
public final class RocketMqTracing {

  /** Returns a new {@link RocketMqTracing} configured with the given {@link OpenTelemetry}. */
  public static RocketMqTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RocketMqTracingBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RocketMqTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new RocketMqTracingBuilder(openTelemetry);
  }

  private final RocketMqConsumerInstrumenter rocketMqConsumerInstrumenter;
  private final Instrumenter<SendMessageContext, Void> rocketMqProducerInstrumenter;

  RocketMqTracing(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {
    rocketMqConsumerInstrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes, propagationEnabled);
    rocketMqProducerInstrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes, propagationEnabled);
  }

  /**
   * Returns a new {@link ConsumeMessageHook} for use with methods like {@link
   * org.apache.rocketmq.client.impl.consumer.DefaultMQPullConsumerImpl#registerConsumeMessageHook(ConsumeMessageHook)}.
   */
  public ConsumeMessageHook newTracingConsumeMessageHook() {
    return new TracingConsumeMessageHookImpl(rocketMqConsumerInstrumenter);
  }

  /**
   * Returns a new {@link SendMessageHook} for use with methods like {@link
   * org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl#registerSendMessageHook(SendMessageHook)}.
   */
  public SendMessageHook newTracingSendMessageHook() {
    return new TracingSendMessageHookImpl(rocketMqProducerInstrumenter);
  }
}
