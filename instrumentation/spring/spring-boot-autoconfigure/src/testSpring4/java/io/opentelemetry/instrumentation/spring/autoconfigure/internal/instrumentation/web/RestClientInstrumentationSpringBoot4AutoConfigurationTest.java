/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractRestClientInstrumentationAutoConfigurationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.web.client.RestClient;

class RestClientInstrumentationSpringBoot4AutoConfigurationTest
    extends AbstractRestClientInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(RestClientInstrumentationSpringBoot4AutoConfiguration.class);
  }

  @Override
  protected Class<?> postProcessorClass() {
    return RestClientBeanPostProcessorSpring4.class;
  }

  @Test
  void shouldNotCreateNewBeanWhenInterceptorAlreadyPresent() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              RestClientBeanPostProcessorSpring4 beanPostProcessor =
                  context.getBean(
                      "otelRestClientBeanPostProcessor", RestClientBeanPostProcessorSpring4.class);

              RestClient restClientWithInterceptor =
                  RestClient.builder()
                      .requestInterceptor(
                          RestClientBeanPostProcessor.getInterceptor(
                              context.getBean(OpenTelemetry.class)))
                      .build();

              RestClient processed =
                  (RestClient)
                      beanPostProcessor.postProcessAfterInitialization(
                          restClientWithInterceptor, "testBean");

              // Should return the same instance when interceptor is already present
              assertThat(processed).isSameAs(restClientWithInterceptor);

              // Verify only one interceptor exists
              processed
                  .mutate()
                  .requestInterceptors(
                      interceptors -> {
                        long count =
                            interceptors.stream()
                                .filter(
                                    rti ->
                                        rti.getClass()
                                            .getName()
                                            .startsWith("io.opentelemetry.instrumentation"))
                                .count();
                        assertThat(count).isEqualTo(1);
                      });
            });
  }
}
