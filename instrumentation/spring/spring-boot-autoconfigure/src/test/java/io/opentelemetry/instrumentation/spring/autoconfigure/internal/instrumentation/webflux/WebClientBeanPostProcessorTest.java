/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientBeanPostProcessorTest {
  private static final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

  static {
    beanFactory.registerSingleton("openTelemetry", OpenTelemetry.noop());
    beanFactory.registerSingleton(
        "configProperties", DefaultConfigProperties.createFromMap(Collections.emptyMap()));
  }

  @Test
  @DisplayName(
      "when processed bean is NOT of type WebClient or WebClientBuilder should return Object")
  void returnsObject() {
    BeanPostProcessor underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    assertThat(underTest.postProcessAfterInitialization(new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);
  }

  @Test
  @DisplayName("when processed bean is of type WebClient should return WebClient")
  void returnsWebClient() {
    BeanPostProcessor underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    assertThat(underTest.postProcessAfterInitialization(WebClient.create(), "testWebClient"))
        .isInstanceOf(WebClient.class);
  }

  @Test
  @DisplayName("when processed bean is of type WebClientBuilder should return WebClientBuilder")
  void returnsWebClientBuilder() {
    BeanPostProcessor underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    assertThat(
            underTest.postProcessAfterInitialization(WebClient.builder(), "testWebClientBuilder"))
        .isInstanceOf(WebClient.Builder.class);
  }

  @Test
  @DisplayName("when processed bean is of type WebClient should add exchange filter to WebClient")
  void addsExchangeFilterWebClient() {
    BeanPostProcessor underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    WebClient webClient = WebClient.create();
    Object processedWebClient =
        underTest.postProcessAfterInitialization(webClient, "testWebClient");

    assertThat(processedWebClient).isInstanceOf(WebClient.class);
    ((WebClient) processedWebClient)
        .mutate()
        .filters(
            functions ->
                assertThat(
                        functions.stream()
                            .filter(WebClientBeanPostProcessorTest::isOtelExchangeFilter)
                            .count())
                    .isEqualTo(1));
  }

  @Test
  @DisplayName(
      "when processed bean is of type WebClientBuilder should add ONE exchange filter to WebClientBuilder")
  void addsExchangeFilterWebClientBuilder() {
    BeanPostProcessor underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    WebClient.Builder webClientBuilder = WebClient.builder();
    underTest.postProcessAfterInitialization(webClientBuilder, "testWebClientBuilder");
    underTest.postProcessAfterInitialization(webClientBuilder, "testWebClientBuilder");
    underTest.postProcessAfterInitialization(webClientBuilder, "testWebClientBuilder");

    webClientBuilder.filters(
        functions ->
            assertThat(
                    functions.stream()
                        .filter(WebClientBeanPostProcessorTest::isOtelExchangeFilter)
                        .count())
                .isEqualTo(1));
  }

  private static boolean isOtelExchangeFilter(ExchangeFilterFunction wctf) {
    return wctf.getClass().getName().startsWith("io.opentelemetry.instrumentation");
  }
}
