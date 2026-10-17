/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;

class InterceptorsTest extends AbstractInterceptorsTest {

  private static final boolean MESSAGING_RECEIVE_TELEMETRY_ENABLED =
      Boolean.getBoolean("test.messagingReceiveTelemetryEnabled");
  private static final boolean CAPTURE_MESSAGING_HEADERS =
      Boolean.getBoolean("test.captureMessagingHeaders");
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Boolean.getBoolean("test.captureExperimentalSpanAttributes");
  private static final KafkaTelemetry kafkaTelemetry = createKafkaTelemetry();

  private static KafkaTelemetry createKafkaTelemetry() {
    KafkaTelemetryBuilder builder = KafkaTelemetry.builder(testing.getOpenTelemetry());
    if (MESSAGING_RECEIVE_TELEMETRY_ENABLED) {
      builder.setMessagingReceiveTelemetryEnabled(true);
    }
    if (CAPTURE_MESSAGING_HEADERS) {
      builder.setHeaders(
          IncludeExclude.builder()
              .setIncluded("Test-Message-*")
              .setExcluded("*-Excluded-Header")
              .build());
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.setCaptureExperimentalSpanAttributes(true);
    }
    return builder.build();
  }

  @Override
  protected KafkaTelemetry kafkaTelemetry() {
    return kafkaTelemetry;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }

  @Override
  protected boolean receiveTelemetryEnabled() {
    return MESSAGING_RECEIVE_TELEMETRY_ENABLED;
  }

  @Override
  protected boolean captureHeaders() {
    return CAPTURE_MESSAGING_HEADERS;
  }
}
