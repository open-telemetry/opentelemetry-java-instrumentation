/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingConfig;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqBatchSendHelper;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqBatchSendHelper.BatchSendState;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageHook;

public class RocketMqSingletons {

  private static final IncludeExclude headers = ExperimentalConfig.get().getMessagingHeaders();
  private static final boolean captureExperimentalSpanAttributes =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "rocketmq_client")
          .getBoolean("experimental_span_attributes/development", false);

  private static final RocketMqTelemetry telemetry =
      RocketMqTelemetry.builder(GlobalOpenTelemetry.get())
          .setHeaders(headers)
          .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes)
          .build();

  private static final ScopedThreadValue<BatchSendState> currentBatchSendState =
      new ScopedThreadValue<>();

  private static final RocketMqBatchSendHelper batchSendHelper =
      new RocketMqBatchSendHelper(
          GlobalOpenTelemetry.get(),
          headers,
          captureExperimentalSpanAttributes,
          MessagingConfig.isBatchSendMessageCreationSpansEnabled(
              GlobalOpenTelemetry.get(), "rocketmq_client"));

  private static final ConsumeMessageHook consumeMessageHook = telemetry.createConsumeMessageHook();

  private static final SendMessageHook sendMessageHook =
      batchSendHelper.wrap(telemetry.createSendMessageHook());

  public static RocketMqBatchSendHelper batchSendHelper() {
    return batchSendHelper;
  }

  public static ConsumeMessageHook consumeMessageHook() {
    return consumeMessageHook;
  }

  public static ScopedThreadValue<BatchSendState> currentBatchSendState() {
    return currentBatchSendState;
  }

  public static SendMessageHook sendMessageHook() {
    return sendMessageHook;
  }

  private RocketMqSingletons() {}
}
