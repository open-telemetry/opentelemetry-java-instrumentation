/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2SqsTracingTestWithW3CPropagator extends AbstractAws2SqsTracingTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        AwsSdkTelemetry.builder(getOpenTelemetry())
          .setCaptureExperimentalSpanAttributes(true)
          .setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()) // Difference to main test
          .setUseXrayPropagator(isXrayInjectionEnabled()) // Disable to confirm messaging propagator actually works
          .build()
          .newExecutionInterceptor())
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    true
  }

  @Override
  boolean isXrayInjectionEnabled() {
    false
  }
}
