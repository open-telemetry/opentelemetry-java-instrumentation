/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringApp {

  @Bean
  public TestBean testBean(MeterRegistry meterRegistry) {
    return new TestBean(meterRegistry);
  }

  public static class TestBean {
    private final Counter counter;

    public TestBean(MeterRegistry registry) {
      this.counter =
          Counter.builder("test-counter")
              .baseUnit("thingies")
              .tags("tag", "value")
              .register(registry);
    }

    public void inc() {
      counter.increment();
    }
  }
}
