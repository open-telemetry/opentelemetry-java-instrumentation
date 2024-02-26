/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      OtelSpringStarterSmokeTest.TestConfiguration.class
    },
    properties = {"otel.sdk.disabled=true"})
class OtelSpringStarterDisabledSmokeTest {

  @Test
  void shouldStartApplication() {
    // make sure we can still start the application with the disabled property
  }
}
