/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.List;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    @Override
    protected List<String> getCapturedHeaders() {
      return ConfigPropertiesUtil.getList(
          "otel.instrumentation.messaging.experimental.capture-headers", emptyList());
    }

    @Override
    protected boolean messagingReceiveTelemetryEnabled() {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);
    }

    @Override
    protected boolean captureExperimentalSpanAttributes() {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-span-attributes", false);
    }

    @Override
    protected boolean useMessagingPropagator() {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
    }

    @Override
    protected boolean recordIndividualHttpError() {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false);
    }

    @Override
    protected boolean genaiCaptureMessageContent() {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.genai.capture-message-content", false);
    }
  }

  private AwsSdkSingletons() {}
}
