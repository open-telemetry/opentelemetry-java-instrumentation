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

  protected abstract boolean captureExperimentalSpanAttributes();

  protected abstract boolean messagingReceiveTelemetryEnabled();

  protected abstract boolean useMessagingPropagator();

  protected abstract boolean recordIndividualHttpError();

  protected abstract boolean genaiCaptureMessageContent();

  public AwsSdkTelemetry telemetry() {
    return AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
        .setCapturedHeaders(getCapturedHeaders())
        .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes())
        .setMessagingReceiveTelemetryEnabled(messagingReceiveTelemetryEnabled())
        .setUseConfiguredPropagatorForMessaging(useMessagingPropagator())
        .setRecordIndividualHttpError(recordIndividualHttpError())
        .setGenaiCaptureMessageContent(genaiCaptureMessageContent())
        .build();
  }
}
