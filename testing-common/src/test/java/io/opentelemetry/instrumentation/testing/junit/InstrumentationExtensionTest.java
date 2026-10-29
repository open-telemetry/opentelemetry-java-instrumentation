/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import org.junit.jupiter.api.Test;

class InstrumentationExtensionTest {

  @SuppressWarnings("deprecation")
  @Test
  void expectedPeerServiceForwardsToExpectedServerServiceName() {
    InstrumentationTestRunner runner =
        mock(InstrumentationTestRunner.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    when(runner.expectedServerServiceName()).thenReturn("service");
    InstrumentationExtension extension = new InstrumentationExtension(runner) {};

    assertThat(runner.expectedPeerService()).isEqualTo("service");
    assertThat(extension.expectedPeerService()).isEqualTo("service");
  }
}
