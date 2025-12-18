/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

public abstract class AbstractRestClientInstrumentationAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected abstract Class<?> postProcessorClass();

  protected abstract ClientHttpRequestInterceptor getInterceptor(
      OpenTelemetry openTelemetry, InstrumentationConfig config);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withBean(
              InstrumentationConfig.class,
              () -> new ConfigPropertiesBridge(DefaultConfigProperties.createFromMap(emptyMap())))
          .withBean(RestClient.class, RestClient::create)
          .withConfiguration(autoConfigurations());

  /**
   * Tests the case that users create a {@link RestClient} bean themselves.
   *
   * <pre>{@code
   * @Bean public RestClient restClient() {
   *     return RestClient.create();
   * }
   * }</pre>
   */
  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              assertThat(context.getBean("otelRestClientBeanPostProcessor", postProcessorClass()))
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
            assertThat(context.getBean("otelRestClientBeanPostProcessor", postProcessorClass()))
                .isNotNull());
  }

  @Test
  void shouldNotCreateNewBeanWhenInterceptorAlreadyPresent() {
    contextRunner.run(
        context -> {
          Object beanPostProcessor =
              context.getBean("otelRestClientBeanPostProcessor", postProcessorClass());

          RestClient restClientWithInterceptor =
              RestClient.builder()
                  .requestInterceptor(
                      getInterceptor(
                          context.getBean(OpenTelemetry.class),
                          context.getBean(InstrumentationConfig.class)))
                  .build();

          RestClient processed =
              (RestClient)
                  ((BeanPostProcessor) beanPostProcessor)
                      .postProcessAfterInitialization(restClientWithInterceptor, "testBean");

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
