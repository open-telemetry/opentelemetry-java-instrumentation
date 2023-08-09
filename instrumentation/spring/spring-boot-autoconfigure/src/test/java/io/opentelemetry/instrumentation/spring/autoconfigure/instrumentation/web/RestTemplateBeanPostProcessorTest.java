/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

class RestTemplateBeanPostProcessorTest {

  @Test
  @DisplayName("when processed bean is not of type RestTemplate should return object")
  void returnsObject() {
    BeanPostProcessor underTest = new RestTemplateBeanPostProcessor(OpenTelemetry.noop());

    assertThat(underTest.postProcessAfterInitialization(new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should return RestTemplate")
  void returnsRestTemplate() {
    BeanPostProcessor underTest = new RestTemplateBeanPostProcessor(OpenTelemetry.noop());

    assertThat(underTest.postProcessAfterInitialization(new RestTemplate(), "testRestTemplate"))
        .isInstanceOf(RestTemplate.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should add ONE RestTemplateInterceptor")
  void addsRestTemplateInterceptor() {
    BeanPostProcessor underTest = new RestTemplateBeanPostProcessor(OpenTelemetry.noop());

    RestTemplate restTemplate = new RestTemplate();

    underTest.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    underTest.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    underTest.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertThat(
            restTemplate.getInterceptors().stream()
                .filter(RestTemplateBeanPostProcessorTest::isOtelInstrumentationInterceptor)
                .count())
        .isEqualTo(1);
  }

  private static boolean isOtelInstrumentationInterceptor(ClientHttpRequestInterceptor rti) {
    return rti.getClass().getName().startsWith("io.opentelemetry.instrumentation");
  }
}
