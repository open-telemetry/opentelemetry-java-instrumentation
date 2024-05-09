/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class
    },
    properties = {"otel.sdk.disabled=true"})
@DisabledInNativeImage // Without this the native tests in the OtelSpringStarterSmokeTest class will
// fail with org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "TEST_TABLE"
// already exists
class OtelSpringStarterDisabledSmokeTest {

  @Test
  void shouldStartApplication() {
    // make sure we can still start the application with the disabled property
  }
}
