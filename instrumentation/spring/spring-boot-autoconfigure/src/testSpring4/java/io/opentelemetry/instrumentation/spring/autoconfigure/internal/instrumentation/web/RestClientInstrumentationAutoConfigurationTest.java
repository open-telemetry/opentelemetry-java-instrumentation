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
              AutoConfigurations.of(RestClientInstrumentationSpringBoot4AutoConfiguration.class));

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
              assertThat(
                      context.getBean(
                          "otelRestClientBeanPostProcessor",
                          RestClientBeanPostProcessorSpring4.class))
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
                        "otelRestClientBeanPostProcessor",
                        RestClientBeanPostProcessorSpring4.class))
                .isNotNull());
  }

  /**
   * Tests the case where users inject RestClient.Builder from Spring Boot autoconfiguration.
   *
   * <pre>{@code
   * @Autowired private RestClient.Builder restClientBuilder;
   *
   * public void makeRequest() {
   *     RestClient client = restClientBuilder.build();
   *     client.get().uri("http://example.com").retrieve().body(String.class);
   * }
   * }</pre>
   *
   * <p>This tests the Spring Boot 4 RestClientAutoConfiguration integration to ensure the
   * RestClientCustomizer is properly applied to injected builders.
   */
  @Test
  void restClientBuilderInjection() {
    new ApplicationContextRunner()
        .withBean(OpenTelemetry.class, OpenTelemetry::noop)
        .withBean(
            InstrumentationConfig.class,
            () ->
                new ConfigPropertiesBridge(
                    DefaultConfigProperties.createFromMap(Collections.emptyMap())))
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration.class,
                RestClientInstrumentationSpringBoot4AutoConfiguration.class))
        .withPropertyValues("otel.instrumentation.spring-web.enabled=true")
        .run(
            context -> {
              RestClient.Builder builder = context.getBean(RestClient.Builder.class);
              RestClient client = builder.build();

              // Verify that the built RestClient has instrumentation
              client
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
