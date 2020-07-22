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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
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
  public void should_NOT_initalize_otel_tracer_if_tracer_bean_exists() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(TracerAutoConfiguration.class))
        .run(
            (context) -> {
              assertTrue(
                  "Application Context contains customTestTracer bean",
                  context.containsBean("customTestTracer"));

              assertFalse(
                  "Application Context DOES NOT contain the otelTracer bean defined in TracerAutoConfiguration",
                  context.containsBean("otelTracer"));
            });
  }

  @Test
  public void should_contain_otel_tracer_bean() {
    this.contextRunner
        .withUserConfiguration(TracerAutoConfiguration.class)
        .run(
            (context) -> {
              assertTrue(
                  "Application Context contains otelTracer bean",
                  context.containsBean("otelTracer"));
            });
  }

  @Test
  public void should_set_tracer_name() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.tracer.name=testTracer")
        .withConfiguration(AutoConfigurations.of(TracerAutoConfiguration.class))
        .run(
            (context) -> {
              assertEquals(
                  "Application Context sets the Tracer with name to testTracer",
                  context.getBean("otelTracer", Tracer.class),
                  OpenTelemetry.getTracer("testTracer"));
            });
  }
}
