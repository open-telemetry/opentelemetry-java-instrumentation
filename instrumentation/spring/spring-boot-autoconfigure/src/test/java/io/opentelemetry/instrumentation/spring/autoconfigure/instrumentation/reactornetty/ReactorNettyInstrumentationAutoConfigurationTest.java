/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReactorNettyInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(ReactorNettyInstrumentationAutoConfiguration.class));

  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.reactor-netty.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "reactorNettyHttpClientInitializingBean", ReactorNettyHttpClientInitializingBean.class))
                    .isNotNull());
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.reactor-netty.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("reactorNettyHttpClientInitializingBean")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "reactorNettyHttpClientInitializingBean", ReactorNettyHttpClientInitializingBean.class))
                .isNotNull());
  }
}
