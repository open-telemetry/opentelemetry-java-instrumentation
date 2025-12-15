/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkTelemetryFactory {

  public static AwsSdkTelemetry telemetry() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DeclarativeConfigProperties java =
        DeclarativeConfigUtil.getStructured(openTelemetry, "java", empty());
    DeclarativeConfigProperties awsSdk = java.getStructured("aws_sdk", empty());
    DeclarativeConfigProperties messaging = java.getStructured("messaging", empty());

    return AwsSdkTelemetry.builder(openTelemetry)
        .setCapturedHeaders(
            messaging.getScalarList("capture_headers/development", String.class, emptyList()))
        .setCaptureExperimentalSpanAttributes(
            awsSdk.getBoolean("span_attributes/development", false))
        .setMessagingReceiveTelemetryEnabled(
            messaging
                .getStructured("receive_telemetry/development", empty())
                .getBoolean("enabled", false))
        .setUseConfiguredPropagatorForMessaging(
            awsSdk.getBoolean("use_propagator_for_messaging/development", false))
        .setRecordIndividualHttpError(
            awsSdk.getBoolean("record_individual_http_error/development", false))
        .setGenaiCaptureMessageContent(
            java.getStructured("genai", empty()).getBoolean("capture_message_content", false))
        .build();
  }

  private AwsSdkTelemetryFactory() {}
}
