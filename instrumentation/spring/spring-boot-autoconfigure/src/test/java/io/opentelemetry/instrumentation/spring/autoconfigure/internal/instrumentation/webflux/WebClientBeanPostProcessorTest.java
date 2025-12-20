/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
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
  }

  private BeanPostProcessor underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new WebClientBeanPostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(InstrumentationConfig.class));
  }

  @Test
  @DisplayName("when processed bean is NOT of type WebClient should return same Object")
  void returnsObject() {
    Object original = new Object();

    assertThat(underTest.postProcessAfterInitialization(original, "testObject")).isSameAs(original);
  }

  @Test
  @DisplayName("when processed bean is of type WebClient should return WebClient with filter")
  void returnsWebClientWithFilter() {
    WebClient webClient = WebClient.create();
    Object processedWebClient =
        underTest.postProcessAfterInitialization(webClient, "testWebClient");

    assertThat(processedWebClient).isInstanceOf(WebClient.class).isNotSameAs(webClient);
    assertFilterCount((WebClient) processedWebClient, 1);
  }

  @Test
  @DisplayName("when WebClient already has filter should return same instance")
  void doesNotAddDuplicateFilter() {
    WebClient webClient = WebClient.create();
    WebClient firstProcessed =
        (WebClient) underTest.postProcessAfterInitialization(webClient, "testWebClient");
    WebClient secondProcessed =
        (WebClient) underTest.postProcessAfterInitialization(firstProcessed, "testWebClient");

    assertThat(secondProcessed).isSameAs(firstProcessed);
    assertFilterCount(secondProcessed, 1);
  }

  private static void assertFilterCount(WebClient webClient, long expectedCount) {
    AtomicLong count = new AtomicLong(0);
    webClient
        .mutate()
        .filters(
            filters ->
                count.set(
                    filters.stream()
                        .filter(WebClientBeanPostProcessorTest::isOtelExchangeFilter)
                        .count()));
    assertThat(count.get()).isEqualTo(expectedCount);
  }

  private static boolean isOtelExchangeFilter(ExchangeFilterFunction filter) {
    return filter.getClass().getName().startsWith("io.opentelemetry.instrumentation");
  }
}
