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
    return getBoolean("aws_sdk", "span_attributes/development");
  }

  protected abstract boolean messagingReceiveInstrumentationEnabled();

  private boolean useMessagingPropagator() {
    return getBoolean("aws_sdk", "use_propagator_for_messaging/development");
  }

  private boolean recordIndividualHttpError() {
    return getBoolean("aws_sdk", "record_individual_http_error/development");
  }

  private boolean genaiCaptureMessageContent() {
    return getBoolean("genai", "capture_message_content");
  }

  protected abstract boolean getBoolean(String... name);

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
