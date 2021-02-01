/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link RestTemplateAutoConfiguration}. */
class RestTemplateAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, RestTemplateAutoConfiguration.class));

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

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
