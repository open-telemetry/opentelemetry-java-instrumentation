/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;

class InterceptorsTest extends AbstractInterceptorsTest {

  private static final KafkaTelemetry kafkaTelemetry =
      KafkaTelemetry.builder(testing.getOpenTelemetry())
          .setMessagingReceiveTelemetryEnabled(true)
          .setHeaders(
              IncludeExclude.builder()
                  .setIncluded("Test-Message-*")
                  .setExcluded("*-Excluded-Header")
                  .build())
          .build();

  @Override
  protected KafkaTelemetry kafkaTelemetry() {
    return kafkaTelemetry;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return false;
  }
}
