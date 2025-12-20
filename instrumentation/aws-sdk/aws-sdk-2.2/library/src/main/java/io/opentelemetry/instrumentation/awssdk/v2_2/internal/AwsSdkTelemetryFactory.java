/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.LegacyLibraryConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkTelemetryFactory {

  public static AwsSdkTelemetry telemetry() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    ExtendedDeclarativeConfigProperties awsSdk =
        LegacyLibraryConfigUtil.getJavaInstrumentationConfig(openTelemetry, "aws_sdk");
    ExtendedDeclarativeConfigProperties messaging =
        LegacyLibraryConfigUtil.getJavaInstrumentationConfig(openTelemetry, "messaging");

    return AwsSdkTelemetry.builder(openTelemetry)
        .setCapturedHeaders(
            messaging.getScalarList("capture_headers/development", String.class, emptyList()))
        .setCaptureExperimentalSpanAttributes(
            awsSdk.getBoolean("span_attributes/development", false))
        .setMessagingReceiveTelemetryEnabled(
            messaging.get("receive_telemetry/development").getBoolean("enabled", false))
        .setUseConfiguredPropagatorForMessaging(
            awsSdk.getBoolean("use_propagator_for_messaging/development", false))
        .setRecordIndividualHttpError(
            awsSdk.getBoolean("record_individual_http_error/development", false))
        .setGenaiCaptureMessageContent(
            LegacyLibraryConfigUtil.getJavaInstrumentationConfig(openTelemetry, "genai")
                .getBoolean("capture_message_content", false))
        .build();
  }

  private AwsSdkTelemetryFactory() {}
}
