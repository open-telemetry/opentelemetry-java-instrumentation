/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

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
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RestTemplateBeanPostProcessorTest {

  @Mock ObjectProvider<OpenTelemetry> openTelemetryProvider;

  RestTemplateBeanPostProcessor restTemplateBeanPostProcessor;

  @BeforeEach
  void setUp() {
    restTemplateBeanPostProcessor = new RestTemplateBeanPostProcessor(openTelemetryProvider);
  }

  @Test
  @DisplayName("when processed bean is not of type RestTemplate should return object")
  void returnsObject() {
    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);

    verifyNoInteractions(openTelemetryProvider);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should return RestTemplate")
  void returnsRestTemplate() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new RestTemplate(), "testRestTemplate"))
        .isInstanceOf(RestTemplate.class);

    verify(openTelemetryProvider).getIfUnique();
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should add ONE RestTemplateInterceptor")
  void addsRestTemplateInterceptor() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(OpenTelemetry.noop());

    RestTemplate restTemplate = new RestTemplate();

    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertThat(
            restTemplate.getInterceptors().stream()
                .filter(RestTemplateBeanPostProcessorTest::isOtelInstrumentationInterceptor)
                .count())
        .isEqualTo(1);

    verify(openTelemetryProvider, times(3)).getIfUnique();
  }

  @Test
  @DisplayName("when OpenTelemetry is not available should NOT add RestTemplateInterceptor")
  void doesNotAddRestTemplateInterceptorIfOpenTelemetryUnavailable() {
    when(openTelemetryProvider.getIfUnique()).thenReturn(null);
    RestTemplate restTemplate = new RestTemplate();

    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertThat(
            restTemplate.getInterceptors().stream()
                .filter(RestTemplateBeanPostProcessorTest::isOtelInstrumentationInterceptor)
                .count())
        .isEqualTo(0);

    verify(openTelemetryProvider, times(3)).getIfUnique();
  }

  private static boolean isOtelInstrumentationInterceptor(ClientHttpRequestInterceptor rti) {
    return rti.getClass().getName().startsWith("io.opentelemetry.instrumentation");
  }
}
