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

import io.opentelemetry.instrumentation.springwebmvc.WebMVCTracingFilter;
import io.opentelemetry.trace.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures WebMVCFilter bean */
@Configuration
@EnableConfigurationProperties(WebMVCProperties.class)
@ConditionalOnProperty(prefix = "opentelemetry.trace.web", name = "enabled", matchIfMissing = true)
public class WebMVCFilterAutoConfiguration {

  @Bean
  @Autowired
  public WebMVCTracingFilter otelWebMVCTracingFilter(final Tracer tracer) {
    return new WebMVCTracingFilter(tracer);
  }
}
