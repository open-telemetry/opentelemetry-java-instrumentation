/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import io.opentelemetry.instrumentation.spring.webmvc.WebMvcTracingFilter;
import io.opentelemetry.trace.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/** Configures {@link WebMVCFilter} for tracing. */
@Configuration
@EnableConfigurationProperties(WebMvcProperties.class)
@ConditionalOnProperty(prefix = "opentelemetry.trace.web", name = "enabled", matchIfMissing = true)
@ConditionalOnClass(OncePerRequestFilter.class)
public class WebMvcFilterAutoConfiguration {

  @Bean
  public WebMvcTracingFilter otelWebMvcTracingFilter(final Tracer tracer) {
    return new WebMvcTracingFilter(tracer);
  }
}
