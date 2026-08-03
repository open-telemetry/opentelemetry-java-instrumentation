/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkTelemetryFactory {

  public static AwsSdkTelemetry telemetryForAutoconfigureModule() {
    // The library-autoconfigure module has no programmatic configuration API, and
    // declarative instrumentation configuration is not stable, so it is necessary to
    // support configuration via system properties.
    return telemetry(SystemProperties.ENABLED);
  }

  public static AwsSdkTelemetry telemetry() {
    return telemetry(SystemProperties.DISABLED);
  }

  private static AwsSdkTelemetry telemetry(SystemProperties systemProperties) {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    DeclarativeConfigProperties messaging = commonConfig.get("messaging");

    DeclarativeConfigProperties awsSdk =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "aws_sdk");

    return AwsSdkTelemetry.builder(openTelemetry)
        .setCapturedHeaders(
            messaging.getScalarList(
                "capture_headers/development",
                String.class,
                systemProperties.getList(
                    "otel.instrumentation.messaging.experimental.capture-headers", emptyList())))
        .setCaptureExperimentalSpanAttributes(
            awsSdk.getBoolean(
                "experimental_span_attributes/development",
                systemProperties.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-span-attributes", false)))
        .setMessagingReceiveTelemetryEnabled(
            messaging
                .get("receive_telemetry/development")
                .getBoolean(
                    "enabled",
                    systemProperties.getBoolean(
                        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled",
                        false)))
        .setUseConfiguredPropagatorForMessaging(
            awsSdk.getBoolean(
                "use_propagator_for_messaging/development",
                systemProperties.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging",
                    false)))
        .setRecordIndividualHttpError(
            awsSdk.getBoolean(
                "record_individual_http_error/development",
                systemProperties.getBoolean(
                    "otel.instrumentation.aws-sdk.experimental-record-individual-http-error",
                    false)))
        .setGenaiCaptureMessageContent(
            commonConfig
                .get("gen_ai")
                .getBoolean(
                    "capture_message_content",
                    systemProperties.getBoolean(
                        "otel.instrumentation.genai.capture-message-content", false)))
        .build();
  }

  private AwsSdkTelemetryFactory() {}

  private enum SystemProperties {
    ENABLED {
      @Override
      List<String> getList(String key, List<String> defaultValue) {
        return SystemProperty.getList(key, defaultValue);
      }

      @Override
      boolean getBoolean(String key, boolean defaultValue) {
        return SystemProperty.getBoolean(key, defaultValue);
      }
    },
    DISABLED {
      @Override
      List<String> getList(String key, List<String> defaultValue) {
        return defaultValue;
      }

      @Override
      boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
      }
    };

    abstract List<String> getList(String key, List<String> defaultValue);

    abstract boolean getBoolean(String key, boolean defaultValue);
  }
}
