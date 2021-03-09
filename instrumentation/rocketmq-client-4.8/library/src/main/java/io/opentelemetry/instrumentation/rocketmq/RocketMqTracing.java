/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqTracing {
  public static RocketMqTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry)
        .setPropagationEnabled(
            Config.get()
                .getBooleanProperty("otel.instrumentation.rocketmq-client.propagation", true))
        .setCaptureExperimentalSpanAttributes(
            Config.get()
                .getBooleanProperty(
                    "otel.instrumentation.rocketmq-client.experimental-span-attributes", true))
        .build();
  }

  public static RocketMqTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new RocketMqTracingBuilder(openTelemetry);
  }

  private final boolean captureExperimentalSpanAttributes;
  private final boolean propagationEnabled;

  private final RocketMqConsumerTracer rocketMqConsumerTracer;
  private final RocketMqProducerTracer rocketMqProducerTracer;

  RocketMqTracing(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.propagationEnabled = propagationEnabled;
    rocketMqConsumerTracer =
        new RocketMqConsumerTracer(
            openTelemetry, captureExperimentalSpanAttributes, propagationEnabled);
    rocketMqProducerTracer =
        new RocketMqProducerTracer(openTelemetry, captureExperimentalSpanAttributes);
  }

  public ConsumeMessageHook newTracingConsumeMessageHook() {
    return new TracingConsumeMessageHookImpl(rocketMqConsumerTracer);
  }

  public SendMessageHook newTracingSendMessageHook() {
    return new TracingSendMessageHookImpl(rocketMqProducerTracer, propagationEnabled);
  }
}
