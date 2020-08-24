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

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link TraceAspectAutoConfiguration} */
public class TraceAspectAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, TraceAspectAutoConfiguration.class));

  @Test
  @DisplayName("when aspects are ENABLED should initialize WithSpanAspect bean")
  void aspectsEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.aspects.enabled=true")
        .run(
            (context) -> {
              assertThat(context.getBean("withSpanAspect", WithSpanAspect.class)).isNotNull();
            });
  }

  @Test
  @DisplayName("when aspects are DISABLED should NOT initialize WithSpanAspect bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.aspects.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("withSpanAspect")).isFalse();
            });
  }

  @Test
  @DisplayName("when aspects enabled property is MISSING should initialize WithSpanAspect bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(context.getBean("withSpanAspect", WithSpanAspect.class)).isNotNull();
        });
  }
}
