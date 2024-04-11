/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import java.util.List;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

class RestTemplateInstrumentation {

  private RestTemplateInstrumentation() {}

  @CanIgnoreReturnValue
  static RestTemplate addIfNotPresent(RestTemplate restTemplate, OpenTelemetry openTelemetry) {
    ClientHttpRequestInterceptor instrumentationInterceptor =
        SpringWebTelemetry.create(openTelemetry).newInterceptor();

    List<ClientHttpRequestInterceptor> restTemplateInterceptors = restTemplate.getInterceptors();
    if (restTemplateInterceptors.stream()
        .noneMatch(
            interceptor -> interceptor.getClass() == instrumentationInterceptor.getClass())) {
      restTemplateInterceptors.add(0, instrumentationInterceptor);
    }
    return restTemplate;
  }
}
