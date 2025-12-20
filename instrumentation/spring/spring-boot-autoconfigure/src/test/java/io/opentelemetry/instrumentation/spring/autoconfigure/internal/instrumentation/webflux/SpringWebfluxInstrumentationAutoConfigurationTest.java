/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

class SpringWebfluxInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(SpringWebfluxInstrumentationAutoConfiguration.class));

  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasBean("otelWebClientBeanPostProcessor")
                    .hasBean("otelWebClientCustomizer"));
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean("otelWebClientBeanPostProcessor")
                    .doesNotHaveBean("otelWebClientCustomizer"));
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context)
              .hasBean("otelWebClientBeanPostProcessor")
              .hasBean("otelWebClientCustomizer");
        });
  }

  @Test
  void shouldAddTracingFilterWhenCustomizerApplied() {
    contextRunner.run(
        context -> {
          WebClientCustomizer customizer =
              context.getBean("otelWebClientCustomizer", WebClientCustomizer.class);
          WebClient.Builder builder = WebClient.builder();
          customizer.customize(builder);

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
}
