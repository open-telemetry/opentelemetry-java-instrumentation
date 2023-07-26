/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class, DispatcherServlet.class})
@ConditionalOnProperty(name = "otel.instrumentation.spring-webmvc.enabled", matchIfMissing = true)
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class SpringWebMvc5InstrumentationAutoConfiguration {

  @Bean
  Filter otelWebMvcFilter(OpenTelemetry openTelemetry) {
    return SpringWebMvcTelemetry.create(openTelemetry).createServletFilter();
  }
}
