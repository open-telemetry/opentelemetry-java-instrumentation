/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractRestClientInstrumentationAutoConfigurationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.web.client.RestClient;

class RestClientInstrumentationAutoConfigurationTest
    extends AbstractRestClientInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(RestClientInstrumentationAutoConfiguration.class);
  }

  @Override
  protected Class<?> postProcessorClass() {
    return RestClientBeanPostProcessor.class;
  }

  @Test
  void shouldNotCreateNewBeanWhenInterceptorAlreadyPresent() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              RestClientBeanPostProcessor beanPostProcessor =
                  context.getBean(
                      "otelRestClientBeanPostProcessor", RestClientBeanPostProcessor.class);

              RestClient restClientWithInterceptor =
                  RestClient.builder()
                      .requestInterceptor(
                          RestClientBeanPostProcessor.getInterceptor(
                              context.getBean(OpenTelemetry.class),
                              context.getBean(InstrumentationConfig.class)))
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
