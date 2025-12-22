/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Abstract base test for WebClient customizer auto-configurations. Subclasses must provide the
 * auto-configuration class and WebClientCustomizer class for their Spring Boot version.
 */
public abstract class AbstractWebClientCustomizerAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected abstract Class<?> webClientCustomizerClass();

  protected abstract void customizeWebClient(Object customizer, WebClient.Builder builder);

  protected ApplicationContextRunner contextRunner;

  @BeforeEach
  void setUp() {
    contextRunner =
        new ApplicationContextRunner()
            .withBean(OpenTelemetry.class, OpenTelemetry::noop)
            .withConfiguration(autoConfigurations());
  }

  @Test
  void shouldCreateCustomizerWhenEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelWebClientCustomizer"))
                    .isNotNull()
                    .isInstanceOf(webClientCustomizerClass()));
  }

  @Test
  void shouldNotCreateCustomizerWhenDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("otelWebClientCustomizer"));
  }

  @Test
  void shouldCreateCustomizerByDefault() {
    contextRunner.run(
        context ->
            assertThat(context.getBean("otelWebClientCustomizer"))
                .isNotNull()
                .isInstanceOf(webClientCustomizerClass()));
  }

  @Test
  void shouldAddTracingFilterWhenCustomizerApplied() {
    contextRunner.run(
        context -> {
          Object customizer =
              context.getBean("otelWebClientCustomizer", webClientCustomizerClass());
          WebClient.Builder builder = WebClient.builder();
          customizeWebClient(customizer, builder);

          AtomicLong count = new AtomicLong(0);
          builder
              .build()
              .mutate()
              .filters(
                  filters ->
                      count.set(
                          filters.stream()
                              .filter(
                                  f ->
                                      f.getClass()
                                          .getName()
                                          .startsWith("io.opentelemetry.instrumentation"))
                              .count()));
          assertThat(count.get()).isEqualTo(1);
        });
  }

  @Test
  void shouldNotCreateCustomizerWhenWebClientCustomizerNotOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(webClientCustomizerClass()))
        .run(context -> assertThat(context).doesNotHaveBean("otelWebClientCustomizer"));
  }

  @Test
  void shouldNotCreateCustomizerWhenWebClientNotOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(WebClient.class))
        .run(context -> assertThat(context).doesNotHaveBean("otelWebClientCustomizer"));
  }
}
