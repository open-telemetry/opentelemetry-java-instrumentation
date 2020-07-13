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

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.trace.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create an io.opentelemetry.trace.Tracer bean <br>
 * If TracerProperties.loggingExporterIsEnabled=True: Create a simple span processor using the
 * LoggingSpanExporter
 */
@Configuration
@EnableConfigurationProperties(TracerProperties.class)
public class TracerAutoConfiguration {

  @Autowired TracerProperties tracerProperties;

  @Bean
  public Tracer tracer() throws Exception {
    Tracer tracer = OpenTelemetry.getTracer(tracerProperties.getName());
    setLoggingExporter();
    return tracer;
  }

  private void setLoggingExporter() {
    if (!tracerProperties.isLoggingExporterIsEnabled()) return;

    SpanProcessor logProcessor = SimpleSpanProcessor.newBuilder(new LoggingSpanExporter()).build();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(logProcessor);
  }
}
