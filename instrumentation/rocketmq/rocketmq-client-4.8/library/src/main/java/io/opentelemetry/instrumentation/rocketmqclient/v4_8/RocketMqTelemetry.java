/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.ClientConfig;
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
   *
   * <p>Use {@link #createConsumeMessageHook(ClientConfig)} to capture configured NameServer
   * endpoints.
   */
  public ConsumeMessageHook createConsumeMessageHook() {
    return new TracingConsumeMessageHookImpl(rocketMqConsumerInstrumenter, Attributes.empty());
  }

  /**
   * Returns a consume hook using the client's configured NameServer endpoints.
   *
   * <p>Configure the client before calling this method. The hook captures a snapshot of the
   * configuration and should be registered on that client.
   */
  public ConsumeMessageHook createConsumeMessageHook(ClientConfig clientConfig) {
    requireNonNull(clientConfig, "clientConfig");
    return new TracingConsumeMessageHookImpl(
        rocketMqConsumerInstrumenter,
        RocketMqServerAttributesHelper.createAttributes(clientConfig.getNamesrvAddr()));
  }

  /**
   * Returns an instrumented producer that emits telemetry and propagates context for single-message
   * and batch sends.
   *
   * <p>Configure the supplied producer before calling this method, and send messages through the
   * returned producer. It registers its send hook automatically. Use the collection-based {@code
   * send} overloads for batches so context can be injected before encoding.
   */
  public MQProducer instrument(DefaultMQProducer producer) {
    return RocketMqProducerWrapper.wrap(producer, rocketMqProducerInstrumenter, propagator);
  }

  /**
   * Returns a new {@link SendMessageHook} for use with methods like {@link
   * org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl#registerSendMessageHook(SendMessageHook)}.
   *
   * @deprecated Use {@link #instrument(DefaultMQProducer)} instead. May be removed in the next
   *     minor release.
   */
  @Deprecated // may be removed in the next minor release
  public SendMessageHook createSendMessageHook() {
    return RocketMqProducerWrapper.createSendMessageHook(rocketMqProducerInstrumenter);
  }
}
