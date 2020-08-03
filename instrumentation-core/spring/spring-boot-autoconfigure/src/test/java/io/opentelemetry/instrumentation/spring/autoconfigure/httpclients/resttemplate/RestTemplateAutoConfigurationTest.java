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

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link RestTemplateAutoConfiguration} */
public class RestTemplateAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, RestTemplateAutoConfiguration.class));

  @Test
  @DisplayName("when httpclients are ENABLED should initialize RestTemplateInterceptor bean")
  void httpClientsEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=true")
        .run(
            (context) -> {
              assertThat(
                      context.getBean(
                          "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class))
                  .isNotNull();
            });
  }

  @Test
  @DisplayName("when httpclients are DISABLED should NOT initialize RestTemplateInterceptor bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelRestTemplateBeanPostProcessor")).isFalse();
            });
  }

  @Test
  @DisplayName(
      "when httpclients enabled property is MISSING should initialize RestTemplateInterceptor bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(
                  context.getBean(
                      "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class))
              .isNotNull();
        });
  }
}
