/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.instrumentation.spring-web.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class SpringWebInstrumentationAutoConfiguration {

  public SpringWebInstrumentationAutoConfiguration() {}

  @ConditionalOnClass(RestTemplate.class)
  @Bean
  static RestTemplateCustomizer otelRestTemplateCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return restTemplate -> {
      ClientHttpRequestInterceptor instrumentationInterceptor =
          SpringWebTelemetry.create(openTelemetryProvider.getObject()).newInterceptor();
      List<ClientHttpRequestInterceptor> restTemplateInterceptors = restTemplate.getInterceptors();
      if (restTemplateInterceptors.stream()
          .noneMatch(
              interceptor -> interceptor.getClass() == instrumentationInterceptor.getClass())) {
        restTemplateInterceptors.add(0, instrumentationInterceptor);
      }
    };
  }
}
