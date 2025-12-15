/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.Collections;
import java.util.List;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    private final Configuration config = new Configuration(GlobalOpenTelemetry.get());

    @Override
    protected List<String> getCapturedHeaders() {
      return config.capturedHeaders;
    }

    @Override
    protected boolean messagingReceiveTelemetryEnabled() {
      return config.messagingReceiveTelemetryEnabled;
    }

    @Override
    protected boolean captureExperimentalSpanAttributes() {
      return config.captureExperimentalSpanAttributes;
    }

    @Override
    protected boolean useMessagingPropagator() {
      return config.useMessagingPropagator;
    }

    @Override
    protected boolean recordIndividualHttpError() {
      return config.recordIndividualHttpError;
    }

    @Override
    protected boolean genaiCaptureMessageContent() {
      return config.genaiCaptureMessageContent;
    }

    // instrumentation/development:
    //   java:
    //     aws_sdk:
    //       experimental_span_attributes: false
    //       experimental_use_propagator_for_messaging: false
    //       experimental_record_individual_http_error: false
    //     genai:
    //       capture_message_content: false
    //     messaging:
    //       receive_telemetry/development:
    //         enabled: false
    //       capture_headers/development: []
    private static final class Configuration {

      private final List<String> capturedHeaders;
      private final boolean messagingReceiveTelemetryEnabled;
      private final boolean captureExperimentalSpanAttributes;
      private final boolean useMessagingPropagator;
      private final boolean recordIndividualHttpError;
      private final boolean genaiCaptureMessageContent;

      Configuration(OpenTelemetry openTelemetry) {
        DeclarativeConfigProperties javaConfig = empty();
        if (openTelemetry instanceof ExtendedOpenTelemetry) {
          ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
          DeclarativeConfigProperties instrumentationConfig =
              extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
          if (instrumentationConfig != null) {
            javaConfig = instrumentationConfig.getStructured("java", empty());
          }
        }
        DeclarativeConfigProperties awsSdkConfig = javaConfig.getStructured("aws_sdk", empty());
        DeclarativeConfigProperties genaiConfig = javaConfig.getStructured("genai", empty());
        DeclarativeConfigProperties messagingConfig =
            javaConfig.getStructured("messaging", empty());

        this.capturedHeaders =
            messagingConfig.getScalarList(
                "capture_headers/development", String.class, Collections.emptyList());
        this.messagingReceiveTelemetryEnabled =
            messagingConfig
                .getStructured("receive_telemetry/development", empty())
                .getBoolean("enabled", false);
        this.captureExperimentalSpanAttributes =
            awsSdkConfig.getBoolean("experimental_span_attributes", false);
        this.useMessagingPropagator =
            awsSdkConfig.getBoolean("experimental_use_propagator_for_messaging", false);
        this.recordIndividualHttpError =
            awsSdkConfig.getBoolean("experimental_record_individual_http_error", false);
        this.genaiCaptureMessageContent = genaiConfig.getBoolean("capture_message_content", false);
      }
    }
  }

  private AwsSdkSingletons() {}
}
