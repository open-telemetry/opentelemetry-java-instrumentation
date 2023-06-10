/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropagationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, PropagationAutoConfiguration.class));

  @Test
  @DisplayName("when propagation is ENABLED should initialize PropagationAutoConfiguration bean")
  void shouldBeConfigured() {

    this.contextRunner
        .withPropertyValues("otel.propagation.enabled=true")
        .run(context -> assertThat(context.containsBean("propagationAutoConfiguration")).isTrue());
  }

  @Test
  @DisplayName(
      "when propagation is DISABLED should NOT initialize PropagationAutoConfiguration bean")
  void shouldNotBeConfigured() {

    this.contextRunner
        .withPropertyValues("otel.propagation.enabled=false")
        .run(context -> assertThat(context.containsBean("propagationAutoConfiguration")).isFalse());
  }

  @Test
  @DisplayName(
      "when propagation enabled property is MISSING should initialize PropagationAutoConfiguration bean")
  void noProperty() {
    this.contextRunner.run(
        context -> assertThat(context.containsBean("propagationAutoConfiguration")).isTrue());
  }

  @Test
  @DisplayName("when no propagators are defined should contain default propagators")
  void shouldContainDefaults() {

    this.contextRunner.run(
        context ->
            assertThat(
                    context.getBean("compositeTextMapPropagator", TextMapPropagator.class).fields())
                .contains("traceparent", "baggage"));
  }

  @Test
  @DisplayName("when propagation is set to b3 should contain only b3 propagator")
  void shouldContainB3() {
    this.contextRunner
        .withPropertyValues("otel.propagation.type=b3")
        .run(
            context -> {
              TextMapPropagator compositePropagator =
                  context.getBean("compositeTextMapPropagator", TextMapPropagator.class);

              assertThat(compositePropagator.fields())
                  .contains("b3")
                  .doesNotContain("baggage", "traceparent");
            });
  }

  @Test
  @DisplayName("when propagation is set to unsupported value should create an empty propagator")
  void shouldCreateNoop() {

    this.contextRunner
        .withPropertyValues("otel.propagation.type=invalid")
        .run(
            context -> {
              TextMapPropagator compositePropagator =
                  context.getBean("compositeTextMapPropagator", TextMapPropagator.class);

              assertThat(compositePropagator.fields()).isEmpty();
            });
  }

  @Test
  @DisplayName("when propagation is set to some values should contain only supported values")
  void shouldContainOnlySupported() {
    this.contextRunner
        .withPropertyValues("otel.propagation.type=invalid,b3")
        .run(
            context -> {
              TextMapPropagator compositePropagator =
                  context.getBean("compositeTextMapPropagator", TextMapPropagator.class);

              assertThat(compositePropagator.fields()).containsExactly("b3");
            });
  }
}
