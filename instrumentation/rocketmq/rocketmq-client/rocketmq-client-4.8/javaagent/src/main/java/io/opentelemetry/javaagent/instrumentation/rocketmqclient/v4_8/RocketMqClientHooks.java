/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.logging.Logger;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqClientHooks {

  private static final Logger logger = Logger.getLogger(RocketMqClientHooks.class.getName());

  @SuppressWarnings("deprecation") // call to deprecated method will be removed in the future
  private static final RocketMqTelemetry TELEMETRY =
      RocketMqTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
          .setPropagationEnabled(
              InstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.rocketmq-client.propagation", true))
          .setCaptureExperimentalSpanAttributes(
              InstrumentationConfig.get()
                  .getBoolean(
                      "otel.instrumentation.rocketmq-client.experimental-span-attributes", false))
          .build();

  static {
    if (InstrumentationConfig.get().getString("otel.instrumentation.rocketmq-client.propagation")
        != null) {
      logger.warning(
          "The \"otel.instrumentation.rocketmq-client.propagation\" configuration property has"
              + " been deprecated and will be removed in a future version."
              + " If you have a need for this configuration property, please open an issue in the"
              + " opentelemetry-java-instrumentation repository:"
              + " https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues");
    }
  }

  public static final ConsumeMessageHook CONSUME_MESSAGE_HOOK =
      TELEMETRY.newTracingConsumeMessageHook();

  public static final SendMessageHook SEND_MESSAGE_HOOK = TELEMETRY.newTracingSendMessageHook();

  private RocketMqClientHooks() {}
}
