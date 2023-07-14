/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass({OncePerRequestFilter.class, DispatcherServlet.class})
@ConditionalOnProperty(name = "otel.instrumentation.spring-webmvc.enabled", matchIfMissing = true)
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class SpringWebMvcInstrumentationAutoConfiguration {

  @ConditionalOnClass(javax.servlet.Filter.class)
  static class Spring5Config {

    @Bean
    javax.servlet.Filter otelWebMvc5Filter(OpenTelemetry openTelemetry) {
      return io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry.create(
              openTelemetry)
          .createServletFilter();
    }
  }

  @ConditionalOnClass(jakarta.servlet.Filter.class)
  static class Spring6Config {

    @Bean
    jakarta.servlet.Filter otelWebMvc6Filter(OpenTelemetry openTelemetry) {
      return io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry.create(
              openTelemetry)
          .createServletFilter();
    }
  }
}
