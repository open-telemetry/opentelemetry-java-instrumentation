/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

/** We want to test the combination of W3C + Xray, as that's what you'll get in prod if you enable W3C. */
class Aws2SqsTracingTestWithW3CPropagatorAndXrayPropagator extends AbstractAws2SqsTracingTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        AwsSdkTelemetry.builder(getOpenTelemetry())
          .setCaptureExperimentalSpanAttributes(true)
          .setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()) // Difference to main test
          .build()
          .newExecutionInterceptor())
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    true
  }
}
