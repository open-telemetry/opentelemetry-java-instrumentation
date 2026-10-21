/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SamplerAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none")
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

  @Test
  void configuresFlatSampler() {
    contextRunner
        .withPropertyValues("otel.traces.sampler=always_off")
        .run(
            context -> {
              OpenTelemetrySdk sdk = context.getBean("openTelemetry", OpenTelemetrySdk.class);
              assertThat(sdk.getSdkTracerProvider().getSampler().getDescription())
                  .isEqualTo(Sampler.alwaysOff().getDescription());
            });
  }
}
