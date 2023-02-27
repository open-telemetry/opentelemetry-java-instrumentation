/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/** Configures {@link SpringWebMvcTelemetry} for tracing. */
@Configuration
@EnableConfigurationProperties(WebMvcProperties.class)
@ConditionalOnProperty(prefix = "otel.springboot.web", name = "enabled", matchIfMissing = true)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class, DispatcherServlet.class})
@ConditionalOnBean(OpenTelemetry.class)
public class WebMvcFilterAutoConfiguration {

  @Bean
  public Filter otelWebMvcInstrumentationFilter(OpenTelemetry openTelemetry) {
    return SpringWebMvcTelemetry.create(openTelemetry).createServletFilter();
  }
}
