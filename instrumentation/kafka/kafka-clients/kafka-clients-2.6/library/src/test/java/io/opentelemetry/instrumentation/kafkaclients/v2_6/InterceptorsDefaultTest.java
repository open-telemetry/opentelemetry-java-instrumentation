/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

class InterceptorsDefaultTest extends AbstractInterceptorsTest {

  private static final KafkaTelemetry kafkaTelemetry =
      KafkaTelemetry.create(testing.getOpenTelemetry());

  @Override
  protected KafkaTelemetry kafkaTelemetry() {
    return kafkaTelemetry;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return false;
  }

  @Override
  protected boolean receiveTelemetryEnabled() {
    return false;
  }
}
