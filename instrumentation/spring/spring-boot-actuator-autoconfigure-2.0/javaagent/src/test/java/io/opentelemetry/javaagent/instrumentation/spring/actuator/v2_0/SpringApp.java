/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    excludeName =
        "org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusSimpleclientMetricsExportAutoConfiguration")
class SpringApp {

  @Bean
  TestBean testBean(MeterRegistry meterRegistry) {
    return new TestBean(meterRegistry);
  }

  static class TestBean {
    private final Counter counter;

    TestBean(MeterRegistry registry) {
      this.counter =
          Counter.builder("test-counter")
              .baseUnit("thingies")
              .tags("tag", "value")
              .register(registry);
    }

    void inc() {
      counter.increment();
    }
  }
}
