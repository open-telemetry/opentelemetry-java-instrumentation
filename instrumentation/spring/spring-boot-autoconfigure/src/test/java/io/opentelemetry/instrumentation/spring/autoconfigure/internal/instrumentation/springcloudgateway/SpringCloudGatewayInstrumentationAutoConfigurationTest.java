/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.springcloudgateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0.SpringCloudGatewayRouteFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SpringCloudGatewayInstrumentationAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(SpringCloudGatewayInstrumentationAutoConfiguration.class));

  @Test
  void instrumentationEnabledByDefault() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(SpringCloudGatewayRouteFilter.class));
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-cloud-gateway.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(SpringCloudGatewayRouteFilter.class));
  }

  @Test
  void gatewayAbsent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.cloud.gateway"))
        .run(context -> assertThat(context).doesNotHaveBean(SpringCloudGatewayRouteFilter.class));
  }

  @Test
  void applicationProvidedFilter() {
    contextRunner
        .withUserConfiguration(CustomFilterConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpringCloudGatewayRouteFilter.class);
              assertThat(context.getBean(SpringCloudGatewayRouteFilter.class))
                  .isSameAs(context.getBean("customRouteFilter"));
            });
  }

  @Configuration(proxyBeanMethods = false)
  static final class CustomFilterConfiguration {
    @Bean
    SpringCloudGatewayRouteFilter customRouteFilter() {
      return new SpringCloudGatewayRouteFilter();
    }
  }
}
