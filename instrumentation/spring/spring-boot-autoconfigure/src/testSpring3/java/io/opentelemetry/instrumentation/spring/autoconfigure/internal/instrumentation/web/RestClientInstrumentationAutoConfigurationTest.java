/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class RestClientInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withBean(
              InstrumentationConfig.class,
              () ->
                  new ConfigPropertiesBridge(
                      DefaultConfigProperties.createFromMap(Collections.emptyMap())))
          .withBean(RestClient.class, RestClient::create)
          .withConfiguration(
              AutoConfigurations.of(RestClientInstrumentationAutoConfiguration.class));

  /**
   * Tests the case that users create a {@link RestClient} bean themselves.
   *
   * <pre>{@code
   * @Bean public RestClient restClient() {
   *     return new RestClient();
   * }
   * }</pre>
   */
  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              assertThat(
                      context.getBean(
                          "otelRestClientBeanPostProcessor", RestClientBeanPostProcessor.class))
                  .isNotNull();

              context
                  .getBean(RestClient.class)
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

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelRestClientBeanPostProcessor")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "otelRestClientBeanPostProcessor", RestClientBeanPostProcessor.class))
                .isNotNull());
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
