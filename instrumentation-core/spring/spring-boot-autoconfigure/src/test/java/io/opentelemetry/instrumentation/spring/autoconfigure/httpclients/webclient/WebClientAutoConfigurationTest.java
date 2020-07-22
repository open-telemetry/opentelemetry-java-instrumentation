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

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebClientAutoConfiguration} */
public class WebClientAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, WebClientAutoConfiguration.class));

  @Test
  public void should_initialize_web_client_bean_post_proccesor_when_httpclients_are_enabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=true")
        .run(
            (context) -> {
              assertNotNull(
                  "Application Context contains WebClientBeanPostProcessor bean",
                  context.getBean(
                      "otelWebClientBeanPostProcessor", WebClientBeanPostProcessor.class));
            });
  }

  @Test
  public void
      should_NOT_initialize_web_client_bean_post_proccesor_when_httpclients_are_NOT_enabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=false")
        .run(
            (context) -> {
              assertFalse(
                  "Application Context DOES NOT contain otelWebClientBeanPostProcessor bean",
                  context.containsBean("otelWebClientBeanPostProcessor"));
            });
  }
}
