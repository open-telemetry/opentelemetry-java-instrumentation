/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class WebClientBeanPostProcessorTest {

  @Mock ObjectProvider<OpenTelemetry> openTelemetryProvider;

  WebClientBeanPostProcessor webClientBeanPostProcessor;

  @BeforeEach
  void setUp() {
    webClientBeanPostProcessor = new WebClientBeanPostProcessor(openTelemetryProvider);
  }

  @Test
  @DisplayName(
      "when processed bean is NOT of type WebClient or WebClientBuilder should return Object")
  void returnsObject() {

    assertThat(
            webClientBeanPostProcessor.postProcessAfterInitialization(new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);

    verifyNoInteractions(openTelemetryProvider);
  }

  @Test
  @DisplayName("when processed bean is of type WebClient should return WebClient")
  void returnsWebClient() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    assertThat(
            webClientBeanPostProcessor.postProcessAfterInitialization(
                WebClient.create(), "testWebClient"))
        .isInstanceOf(WebClient.class);

    verify(openTelemetryProvider).getIfUnique();
  }

  @Test
  @DisplayName("when processed bean is of type WebClientBuilder should return WebClientBuilder")
  void returnsWebClientBuilder() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    assertThat(
            webClientBeanPostProcessor.postProcessAfterInitialization(
                WebClient.builder(), "testWebClientBuilder"))
        .isInstanceOf(WebClient.Builder.class);

    verify(openTelemetryProvider).getIfUnique();
  }

  @Test
  @DisplayName("when processed bean is of type WebClient should add exchange filter to WebClient")
  void addsExchangeFilterWebClient() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    WebClient webClient = WebClient.create();
    Object processedWebClient =
        webClientBeanPostProcessor.postProcessAfterInitialization(webClient, "testWebClient");

    assertThat(processedWebClient).isInstanceOf(WebClient.class);
    ((WebClient) processedWebClient)
        .mutate()
        .filters(
            functions ->
                assertThat(functions.stream().filter(wctf -> isOtelExchangeFilter(wctf)).count())
                    .isEqualTo(1));

    verify(openTelemetryProvider).getIfUnique();
  }

  @Test
  @DisplayName(
      "when processed bean is of type WebClient and OpenTelemetry is not available should NOT add exchange filter to WebClient")
  void doesNotAddExchangeFilterWebClientIfOpenTelemetryUnavailable() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(null);

    WebClient webClient = WebClient.create();
    Object processedWebClient =
        webClientBeanPostProcessor.postProcessAfterInitialization(webClient, "testWebClient");

    assertThat(processedWebClient).isInstanceOf(WebClient.class);
    ((WebClient) processedWebClient)
        .mutate()
        .filters(
            functions ->
                assertThat(functions.stream().filter(wctf -> isOtelExchangeFilter(wctf)).count())
                    .isEqualTo(0));

    verify(openTelemetryProvider).getIfUnique();
  }

  @Test
  @DisplayName(
      "when processed bean is of type WebClientBuilder should add ONE exchange filter to WebClientBuilder")
  void addsExchangeFilterWebClientBuilder() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    WebClient.Builder webClientBuilder = WebClient.builder();
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");

    webClientBuilder.filters(
        functions ->
            assertThat(functions.stream().filter(wctf -> isOtelExchangeFilter(wctf)).count())
                .isEqualTo(1));

    verify(openTelemetryProvider, times(3)).getIfUnique();
  }

  private static boolean isOtelExchangeFilter(ExchangeFilterFunction wctf) {
    return wctf.getClass().getName().startsWith("io.opentelemetry.instrumentation");
  }
}
