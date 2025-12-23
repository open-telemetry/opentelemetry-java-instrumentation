/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkTelemetryFactory {

  public static AwsSdkTelemetry telemetry() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    ExtendedDeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    ExtendedDeclarativeConfigProperties messaging = commonConfig.get("messaging");

    ExtendedDeclarativeConfigProperties awsSdk =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "aws_sdk");

    return AwsSdkTelemetry.builder(openTelemetry)
        .setCapturedHeaders(
            messaging.getScalarList(
                "capture_headers/development",
                String.class,
                ConfigPropertiesUtil.getList(
                    "otel.instrumentation.messaging.experimental.capture-headers", emptyList())))
        .setCaptureExperimentalSpanAttributes(
            awsSdk.getBoolean(
                "experimental_span_attributes/development",
                ConfigPropertiesUtil.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-span-attributes", false)))
        .setMessagingReceiveTelemetryEnabled(
            messaging
                .get("receive_telemetry/development")
                .getBoolean(
                    "enabled",
                    ConfigPropertiesUtil.getBoolean(
                        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled",
                        false)))
        .setUseConfiguredPropagatorForMessaging(
            awsSdk.getBoolean(
                "use_propagator_for_messaging/development",
                ConfigPropertiesUtil.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging",
                    false)))
        .setRecordIndividualHttpError(
            awsSdk.getBoolean(
                "record_individual_http_error/development",
                ConfigPropertiesUtil.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-record-individual-http-error",
                    false)))
        .setGenaiCaptureMessageContent(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
                .get("gen_ai")
                .getBoolean(
                    "capture_message_content",
                    ConfigPropertiesUtil.getBoolean(
                        "otel.instrumentation.genai.capture-message-content", false)))
        .build();
  }

  private AwsSdkTelemetryFactory() {}
}
