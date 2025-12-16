/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqTelemetry;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqClientHooks {

  @SuppressWarnings("deprecation") // call to deprecated method will be removed in the future
  private static final RocketMqTelemetry TELEMETRY;

  static {
    ExtendedDeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.get(GlobalOpenTelemetry.get());
    TELEMETRY =
        RocketMqTelemetry.builder(GlobalOpenTelemetry.get())
            .setCapturedHeaders(
                instrumentationConfig
                    .get("messaging")
                    .getScalarList("capture_headers/development", String.class, emptyList()))
            .setCaptureExperimentalSpanAttributes(
                instrumentationConfig
                    .get("rocketmq-client")
                    .getBoolean("experimental_span_attributes", false))
            .build();
  }

  public static final ConsumeMessageHook CONSUME_MESSAGE_HOOK =
      TELEMETRY.newTracingConsumeMessageHook();

  public static final SendMessageHook SEND_MESSAGE_HOOK = TELEMETRY.newTracingSendMessageHook();

  private RocketMqClientHooks() {}
}
