/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractWebClientCustomizerAutoConfigurationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

class SpringWebfluxInstrumentationAutoConfigurationTest
    extends AbstractWebClientCustomizerAutoConfigurationTest<WebClientCustomizer> {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(SpringWebfluxInstrumentationAutoConfiguration.class);
  }

  @Override
  protected Class<WebClientCustomizer> webClientCustomizerClass() {
    return WebClientCustomizer.class;
  }

  @Override
  protected void customizeWebClient(WebClientCustomizer customizer, WebClient.Builder builder) {
    customizer.customize(builder);
  }

  @Test
  void shouldCreateBeanPostProcessorWhenEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasBean("otelWebClientBeanPostProcessor")
                    .hasBean("telemetryFilter"));
  }

  @Test
  void shouldNotCreateBeanPostProcessorWhenDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean("otelWebClientBeanPostProcessor")
                    .doesNotHaveBean("telemetryFilter"));
  }

  @Test
  void shouldCreateBeanPostProcessorWhenWebClientCustomizerNotOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(WebClientCustomizer.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("otelWebClientBeanPostProcessor")
                    .hasBean("telemetryFilter")
                    .doesNotHaveBean("otelWebClientCustomizer"));
  }
}
