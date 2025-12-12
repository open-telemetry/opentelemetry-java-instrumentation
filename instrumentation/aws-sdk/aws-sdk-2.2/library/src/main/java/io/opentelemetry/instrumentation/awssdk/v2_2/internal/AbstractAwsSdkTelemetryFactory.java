/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import java.util.List;
import java.util.Optional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class AbstractAwsSdkTelemetryFactory {
  protected abstract List<String> getCapturedHeaders();

  private boolean captureExperimentalSpanAttributes() {
    return Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                getConfigProvider(),
                config -> config.getBoolean("span_attributes/development"),
                "aws_sdk"))
        .orElse(false);
  }

  protected abstract boolean messagingReceiveInstrumentationEnabled();

  private boolean useMessagingPropagator() {
    return Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                getConfigProvider(),
                config -> config.getBoolean("use_propagator_for_messaging/development"),
                "aws_sdk"))
        .orElse(false);
  }

  private boolean recordIndividualHttpError() {
    return Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                getConfigProvider(),
                config -> config.getBoolean("record_individual_http_error/development"),
                "aws_sdk"))
        .orElse(false);
  }

  private boolean genaiCaptureMessageContent() {
    return Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                getConfigProvider(),
                config -> config.getBoolean("capture_message_content"),
                "genai"))
        .orElse(false);
  }

  protected abstract ConfigProvider getConfigProvider();

  public AwsSdkTelemetry telemetry() {
    return AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
        .setCapturedHeaders(getCapturedHeaders())
        .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes())
        .setMessagingReceiveInstrumentationEnabled(messagingReceiveInstrumentationEnabled())
        .setUseConfiguredPropagatorForMessaging(useMessagingPropagator())
        .setRecordIndividualHttpError(recordIndividualHttpError())
        .setGenaiCaptureMessageContent(genaiCaptureMessageContent())
        .build();
  }
}
