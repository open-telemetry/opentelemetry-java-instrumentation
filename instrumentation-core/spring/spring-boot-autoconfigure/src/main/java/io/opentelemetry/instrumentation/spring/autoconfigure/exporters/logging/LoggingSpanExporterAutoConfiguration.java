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

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Create LoggingSpanExporter bean*/
@Configuration
@EnableConfigurationProperties(LoggingSpanExporterProperties.class)
@AutoConfigureBefore(TracerAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "opentelemetry.trace.exporter.logging",
    name = "enabled",
    matchIfMissing = true)
@ConditionalOnClass(LoggingSpanExporter.class)
public class LoggingSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingSpanExporter otelLoggingSpanExporter() {
    return new LoggingSpanExporter();
  }
}
