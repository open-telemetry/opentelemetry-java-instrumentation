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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
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
  public void should_initialize_RestTemplateInterceptor_bean_when_httpclients_are_ENABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=true")
        .run(
            (context) -> {
              assertNotNull(
                  "Application Context contains RestTemplateBeanPostProcessor bean",
                  context.getBean(
                      "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class));
            });
  }

  @Test
  public void should_NOT_initialize_RestTemplateInterceptor_bean_when_httpclients_are_DISABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=false")
        .run(
            (context) -> {
              assertFalse(
                  "Application Context DOES NOT contain otelRestTemplateBeanPostProcessor bean",
                  context.containsBean("otelRestTemplateBeanPostProcessor"));
            });
  }

  @Test
  public void
      should_initialize_RestTemplateInterceptor_bean_when_httpclients_enabled_property_is_MISSING() {
    this.contextRunner.run(
        (context) -> {
          assertNotNull(
              "Application Context contains RestTemplateBeanPostProcessor bean",
              context.getBean(
                  "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class));
        });
  }
}
