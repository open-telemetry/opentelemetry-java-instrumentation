/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class AbstractAwsSdkTelemetryFactory {
  protected abstract List<String> getCapturedHeaders();

  private boolean captureExperimentalSpanAttributes() {
    return getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes", false);
  }

  protected abstract boolean messagingReceiveInstrumentationEnabled();

  private boolean useMessagingPropagator() {
    return getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  private boolean recordIndividualHttpError() {
    return getBoolean(
        "otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false);
  }

  private boolean genaiCaptureMessageContent() {
    return getBoolean("otel.instrumentation.genai.capture-message-content", false);
  }

  protected abstract boolean getBoolean(String name, boolean defaultValue);

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
