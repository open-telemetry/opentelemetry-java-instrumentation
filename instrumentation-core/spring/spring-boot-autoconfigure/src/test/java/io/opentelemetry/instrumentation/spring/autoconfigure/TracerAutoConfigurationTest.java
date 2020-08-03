/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link TracerAutoConfiguration} */
public class TracerAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public Tracer customTestTracer() {
      return OpenTelemetry.getTracer("customTestTracer");
    }
  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName("when Application Context contains Tracer bean should NOT initalize otelTracer")
  public void customTracer() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(TracerAutoConfiguration.class))
        .run(
            (context) -> {
              assertThat(context.containsBean("customTestTracer")).isEqualTo(true);
              assertThat(context.containsBean("otelTracer")).isEqualTo(false);
            });
  }

  @Test
  @DisplayName("when Application Context DOES NOT contain Tracer bean should initialize otelTracer")
  public void initalizeTracer() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(TracerAutoConfiguration.class))
        .run(
            (context) -> {
              assertThat(context.containsBean("otelTracer")).isEqualTo(true);
            });
  }

  @Test
  @DisplayName("when opentelemetry.trace.tracer.name is set should initialize tracer with name")
  public void withTracerNameProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.tracer.name=testTracer")
        .withConfiguration(AutoConfigurations.of(TracerAutoConfiguration.class))
        .run(
            (context) -> {
              assertThat(context.getBean("otelTracer", Tracer.class))
                  .isEqualTo(OpenTelemetry.getTracer("testTracer"));
            });
  }
}
