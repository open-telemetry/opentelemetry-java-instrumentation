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
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkTelemetryFactory {

  private final boolean useLegacyLibraryConfig;

  public static AwsSdkTelemetry legacyLibraryTelemetry() {
    return telemetry(true);
  }

  public static AwsSdkTelemetry telemetry() {
    return telemetry(false);
  }

  private static AwsSdkTelemetry telemetry(boolean useLegacyLibraryConfig) {
    AwsSdkTelemetryFactory factory = new AwsSdkTelemetryFactory(useLegacyLibraryConfig);

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
                factory.legacyListValue(
                    "otel.instrumentation.messaging.experimental.capture-headers")))
        .setCaptureExperimentalSpanAttributes(
            awsSdk.getBoolean(
                "experimental_span_attributes/development",
                factory.legacyBooleanValue(
                    "otel.instrumentation.aws-sdk.experimental-span-attributes")))
        .setMessagingReceiveTelemetryEnabled(
            messaging
                .get("receive_telemetry/development")
                .getBoolean(
                    "enabled",
                    factory.legacyBooleanValue(
                        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled")))
        .setUseConfiguredPropagatorForMessaging(
            awsSdk.getBoolean(
                "use_propagator_for_messaging/development",
                factory.legacyBooleanValue(
                    "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging")))
        .setRecordIndividualHttpError(
            awsSdk.getBoolean(
                "record_individual_http_error/development",
                factory.legacyBooleanValue(
                    "otel.instrumentation.aws-sdk.experimental-record-individual-http-error")))
        .setGenaiCaptureMessageContent(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
                .get("gen_ai")
                .getBoolean(
                    "capture_message_content",
                    factory.legacyBooleanValue(
                        "otel.instrumentation.genai.capture-message-content")))
        .build();
  }

  private List<String> legacyListValue(String key) {
    return useLegacyLibraryConfig ? ConfigPropertiesUtil.getList(key, emptyList()) : emptyList();
  }

  private boolean legacyBooleanValue(String key) {
    return useLegacyLibraryConfig && ConfigPropertiesUtil.getBoolean(key, false);
  }

  private AwsSdkTelemetryFactory(boolean useLegacyLibraryConfig) {
    this.useLegacyLibraryConfig = useLegacyLibraryConfig;
  }
}
