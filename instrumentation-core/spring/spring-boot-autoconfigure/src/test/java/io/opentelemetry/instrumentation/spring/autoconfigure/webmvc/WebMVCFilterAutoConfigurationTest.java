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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.springwebmvc.WebMVCTracingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebMVCFilterAutoConfiguration} */
public class WebMVCFilterAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, WebMVCFilterAutoConfiguration.class));

  @Test
  public void should_initialize_web_mvc_tracing_filter_bean_when_web_is_enabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.web.enabled=true")
        .run(
            (context) -> {
              assertNotNull(
                  "Application Context contains WebMVCTracingFilter bean",
                  context.getBean("otelWebMVCTracingFilter", WebMVCTracingFilter.class));
            });
  }

  @Test
  public void should_NOT_initialize_web_mvc_tracing_filter_bean_when_web_is_NOT_enabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.web.enabled=false")
        .run(
            (context) -> {
              assertFalse(
                  "Application Context DOES NOT contain WebMVCTracingFilter bean",
                  context.containsBean("otelWebMVCTracingFilter"));
            });
  }
}
