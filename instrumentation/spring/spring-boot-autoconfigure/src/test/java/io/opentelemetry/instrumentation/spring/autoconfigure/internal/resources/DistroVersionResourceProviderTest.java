/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class DistroVersionResourceProviderTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none")
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

  @Test
  @DisplayName("distro version should be set")
  void hasAttributes() {

    this.contextRunner.run(
        context -> {
          ResourceProvider resource =
              context.getBean("otelDistroVersionResourceProvider", ResourceProvider.class);

          assertThat(
                  resource
                      .createResource(DefaultConfigProperties.createFromMap(ImmutableMap.of()))
                      .getAttributes()
                      .asMap())
              .containsEntry(
                  AttributeKey.stringKey("telemetry.distro.name"),
                  "opentelemetry-spring-boot-starter")
              .anySatisfy(
                  (key, val) -> {
                    assertThat(key.getKey()).isEqualTo("telemetry.distro.version");
                    assertThat(val).asString().isNotBlank();
                  });
        });
  }
}
