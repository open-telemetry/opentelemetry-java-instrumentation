/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RestTemplateBeanPostProcessorTest {
  @Mock Tracer tracer;

  RestTemplateBeanPostProcessor restTemplateBeanPostProcessor =
      new RestTemplateBeanPostProcessor(tracer);

  @Test
  @DisplayName("when processed bean is not of type RestTemplate should return object")
  void returnsObject() {
    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should return RestTemplate")
  void returnsRestTemplate() {
    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new RestTemplate(), "testRestTemplate"))
        .isInstanceOf(RestTemplate.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should add ONE RestTemplateInterceptor")
  void addsRestTemplateInterceptor() {
    RestTemplate restTemplate = new RestTemplate();

    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertThat(
            restTemplate.getInterceptors().stream()
                .filter(rti -> rti instanceof RestTemplateInterceptor)
                .count())
        .isEqualTo(1);
  }
}
