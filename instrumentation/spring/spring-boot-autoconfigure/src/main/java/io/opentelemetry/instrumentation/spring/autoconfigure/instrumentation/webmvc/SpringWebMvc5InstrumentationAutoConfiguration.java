/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class, DispatcherServlet.class})
@Conditional(SpringWebMvc5InstrumentationAutoConfiguration.Condition.class)
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class SpringWebMvc5InstrumentationAutoConfiguration {

  static final class Condition extends AllNestedConditions {
    public Condition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(
        name = "otel.instrumentation.spring-webmvc.enabled",
        matchIfMissing = true)
    static class WebMvc {}

    @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
    static class SdkEnabled {}
  }

  @Bean
  Filter otelWebMvcFilter(OpenTelemetry openTelemetry) {
    return SpringWebMvcTelemetry.create(openTelemetry).createServletFilter();
  }
}
