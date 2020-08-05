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

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.webmvc.WebMvcTracingFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebMvcFilterAutoConfiguration} */
class WebMvcFilterAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, WebMvcFilterAutoConfiguration.class));

  @Test
  @DisplayName("when web is ENABLED should initialize WebMvcTracingFilter bean")
  void webEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.web.enabled=true")
        .run(
            (context) -> {
              assertThat(context.getBean("otelWebMvcTracingFilter", WebMvcTracingFilter.class))
                  .isNotNull();
            });
  }

  @Test
  @DisplayName("when web is DISABLED should NOT initialize WebMvcTracingFilter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.web.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelWebMvcTracingFilter")).isFalse();
            });
  }

  @Test
  @DisplayName("when web property is MISSING should initialize WebMvcTracingFilter bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(context.getBean("otelWebMvcTracingFilter", WebMvcTracingFilter.class))
              .isNotNull();
        });
  }
}
