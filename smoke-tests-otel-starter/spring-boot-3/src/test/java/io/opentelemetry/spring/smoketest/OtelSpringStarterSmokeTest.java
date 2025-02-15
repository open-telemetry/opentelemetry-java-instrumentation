/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import java.util.List;
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
      "otel.instrumentation.runtime-telemetry-java17.enable-all=true",
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Override
  protected void assertAdditionalMetrics() {
    if (!isFlightRecorderAvailable()) {
      return;
    }

    // JFR based metrics
    for (String metric :
        List.of(
            "jvm.cpu.limit",
            "jvm.buffer.count",
            "jvm.class.count",
            "jvm.cpu.context_switch",
            "jvm.cpu.longlock",
            "jvm.system.cpu.utilization",
            "jvm.gc.duration",
            "jvm.memory.init",
            "jvm.memory.used",
            "jvm.memory.allocation",
            "jvm.network.io",
            "jvm.thread.count")) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.runtime-telemetry-java17", metric, AbstractIterableAssert::isNotEmpty);
    }
  }

  private static boolean isFlightRecorderAvailable() {
    try {
      return (boolean)
          Class.forName("jdk.jfr.FlightRecorder").getMethod("isAvailable").invoke(null);
    } catch (ReflectiveOperationException exception) {
      return false;
    }
  }
}
