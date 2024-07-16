/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.internal.SpringMvcBuilderUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-webmvc")
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class, DispatcherServlet.class})
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class SpringWebMvc5InstrumentationAutoConfiguration {

  @Bean
  Filter otelWebMvcFilter(OpenTelemetry openTelemetry, ConfigProperties config) {
    return InstrumentationConfigUtil.configureServerBuilder(
            config,
            SpringWebMvcTelemetry.builder(openTelemetry),
            SpringMvcBuilderUtil.getBuilderExtractor())
        .build()
        .createServletFilter();
  }
}
