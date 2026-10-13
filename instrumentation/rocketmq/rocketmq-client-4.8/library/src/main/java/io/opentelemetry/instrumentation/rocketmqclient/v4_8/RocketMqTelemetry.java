/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;

/** Entrypoint for instrumenting RocketMq producers or consumers. */
public final class RocketMqTelemetry {
  private final RocketMqConsumerInstrumenter rocketMqConsumerInstrumenter;
  private final Instrumenter<SendMessageContext, Void> rocketMqProducerInstrumenter;
  private final TextMapPropagator propagator;

  /** Returns a new {@link RocketMqTelemetry} configured with the given {@link OpenTelemetry}. */
  public static RocketMqTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RocketMqTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RocketMqTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RocketMqTelemetryBuilder(openTelemetry);
  }

  RocketMqTelemetry(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes) {
    rocketMqConsumerInstrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            openTelemetry, headers, captureExperimentalSpanAttributes);
    rocketMqProducerInstrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            openTelemetry, headers, captureExperimentalSpanAttributes);
    propagator = openTelemetry.getPropagators().getTextMapPropagator();
  }

  /**
   * Returns a new {@link ConsumeMessageHook} for use with methods like {@link
   * org.apache.rocketmq.client.impl.consumer.DefaultMQPullConsumerImpl#registerConsumeMessageHook(ConsumeMessageHook)}.
   */
  public ConsumeMessageHook createConsumeMessageHook() {
    return new TracingConsumeMessageHookImpl(rocketMqConsumerInstrumenter);
  }

  /**
   * Returns a decorated producer that emits telemetry and propagates context for single-message and
   * batch sends.
   *
   * <p>Configure the supplied producer before wrapping it, and send messages through the returned
   * producer. The wrapper registers its send hook automatically. Use the collection-based {@code
   * send} overloads for batches so context can be injected before encoding.
   */
  public MQProducer wrap(DefaultMQProducer producer) {
    return RocketMqProducerWrapper.wrap(producer, rocketMqProducerInstrumenter, propagator);
  }

  /**
   * Returns a new {@link SendMessageHook} for use with methods like {@link
   * org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl#registerSendMessageHook(SendMessageHook)}.
   *
   * @deprecated Use {@link #wrap(DefaultMQProducer)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  public SendMessageHook createSendMessageHook() {
    return RocketMqProducerWrapper.createSendMessageHook(rocketMqProducerInstrumenter);
  }
}
