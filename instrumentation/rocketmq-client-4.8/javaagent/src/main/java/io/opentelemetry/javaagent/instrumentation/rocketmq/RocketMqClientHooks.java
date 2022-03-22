/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmq;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.rocketmq.RocketMqTelemetry;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqClientHooks {
  private static final RocketMqTelemetry TELEMETRY =
      RocketMqTelemetry.builder(GlobalOpenTelemetry.get())
          .setPropagationEnabled(
              Config.get().getBoolean("otel.instrumentation.rocketmq-client.propagation", true))
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBoolean(
                      "otel.instrumentation.rocketmq-client.experimental-span-attributes", false))
          .build();

  public static final ConsumeMessageHook CONSUME_MESSAGE_HOOK =
      TELEMETRY.newTracingConsumeMessageHook();

  public static final SendMessageHook SEND_MESSAGE_HOOK = TELEMETRY.newTracingSendMessageHook();
}
