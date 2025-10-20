/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.assertj.core.api.AbstractIterableAssert;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // The headers are simply set here to make sure that headers can be parsed
      "otel.exporter.otlp.headers.c=3",
      "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry=true",
      "otel.instrumentation.common.thread_details.enabled=true",
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Override
  protected void assertAdditionalMetrics() {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.micrometer-1.5", "disk.total", AbstractIterableAssert::isNotEmpty);
  }
}
