/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

/**
 * We want to test the combination of W3C + Xray, as that's what you'll get in prod if you enable
 * W3C.
 */
class Aws2SqsSuppressReceiveSpansW3cPropagatorAndXrayPropagatorTest
    extends Aws2SqsSuppressReceiveSpansTest {

  @Override
  protected void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(
        isSqsAttributeInjectionEnabled()); // Difference to main test
  }

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return true;
  }
}
