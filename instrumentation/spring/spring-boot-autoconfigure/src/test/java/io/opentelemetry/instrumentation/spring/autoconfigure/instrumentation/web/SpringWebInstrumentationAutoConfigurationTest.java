/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

class SpringWebInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withBean(RestTemplate.class, RestTemplate::new)
          .withConfiguration(
              AutoConfigurations.of(SpringWebInstrumentationAutoConfiguration.class));

  /**
   * Tests that users create {@link RestTemplate} bean is instrumented.
   *
   * <pre>{@code
   * @Bean public RestTemplate restTemplate() {
   *     return new RestTemplate();
   * }
   * }</pre>
   */
  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              assertThat(
                      context.getBean(
                          "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class))
                  .isNotNull();

              assertThat(
                      context.getBean(RestTemplate.class).getInterceptors().stream()
                          .filter(
                              rti ->
                                  rti.getClass()
                                      .getName()
                                      .startsWith("io.opentelemetry.instrumentation"))
                          .count())
                  .isEqualTo(1);
            });
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelRestTemplateBeanPostProcessor")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "otelRestTemplateBeanPostProcessor", RestTemplateBeanPostProcessor.class))
                .isNotNull());
  }
}
