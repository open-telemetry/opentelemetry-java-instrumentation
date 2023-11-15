/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;

public final class AwsSdkSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-span-attributes", false);

  private static final boolean USE_MESSAGING_PROPAGATOR =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);

  private static final boolean RECORD_INDIVIDUAL_HTTP_ERROR =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false);

  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);

  private static final AwsSdkTelemetry TELEMETRY =
      AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES)
          .setMessagingReceiveInstrumentationEnabled(RECEIVE_TELEMETRY_ENABLED)
          .setUseConfiguredPropagatorForMessaging(USE_MESSAGING_PROPAGATOR)
          .setRecordIndividualHttpError(RECORD_INDIVIDUAL_HTTP_ERROR)
          .build();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private AwsSdkSingletons() {}
}
