/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqTelemetry;
import java.util.Collections;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqClientHooks {

  @SuppressWarnings("deprecation") // call to deprecated method will be removed in the future
  private static final RocketMqTelemetry TELEMETRY =
      RocketMqTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(
              DeclarativeConfigUtil.getList(
                      GlobalOpenTelemetry.get(),
                      "java",
                      "messaging",
                      "capture_headers/development")
                  .orElse(Collections.emptyList()))
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getBoolean(
                      GlobalOpenTelemetry.get(),
                      "java",
                      "rocketmq-client",
                      "experimental_span_attributes")
                  .orElse(false))
          .build();

  public static final ConsumeMessageHook CONSUME_MESSAGE_HOOK =
      TELEMETRY.newTracingConsumeMessageHook();

  public static final SendMessageHook SEND_MESSAGE_HOOK = TELEMETRY.newTracingSendMessageHook();

  private RocketMqClientHooks() {}
}
