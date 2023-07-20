/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class PropagationPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, PropagationAutoConfiguration.class));

  @Test
  @DisplayName("when propagation is SET should set PropagationProperties with given propagators")
  void hasType() {

    this.contextRunner
        .withPropertyValues("otel.propagation.type=xray,b3")
        .run(
            context -> {
              PropagationProperties propertiesBean = context.getBean(PropagationProperties.class);

              assertThat(propertiesBean.getType()).isEqualTo(Arrays.asList("xray", "b3"));
            });
  }

  @Test
  @DisplayName("when propagation is DEFAULT should set PropagationProperties to default values")
  void hasDefaultTypes() {

    this.contextRunner.run(
        context ->
            assertThat(context.getBean(PropagationProperties.class).getType())
                .containsExactly("tracecontext", "baggage"));
  }
}
