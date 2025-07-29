/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

class Aws2SqsW3cPropagatorTest extends Aws2SqsTracingTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder
        .setUseConfiguredPropagatorForMessaging(
            isSqsAttributeInjectionEnabled()) // Difference to main test
        .setUseXrayPropagator(
            isXrayInjectionEnabled()); // Disable to confirm messaging propagator actually works
  }

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return true;
  }

  @Override
  protected boolean isXrayInjectionEnabled() {
    return false;
  }
}
