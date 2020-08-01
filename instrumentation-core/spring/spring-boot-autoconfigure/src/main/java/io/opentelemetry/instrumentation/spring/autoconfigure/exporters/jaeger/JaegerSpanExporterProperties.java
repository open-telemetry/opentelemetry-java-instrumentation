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

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import static io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter.DEFAULT_DEADLINE_MS;
import static io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter.DEFAULT_ENDPOINT;
import static io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter.DEFAULT_SERVICE_NAME;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link JaegerSpanExporter}
 *
 * <p>Get Exporter Service Name
 *
 * <p>Get Exporter Endpoint
 *
 * <p>Get max wait time for Collector to process Span Batches
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.jaeger")
public final class JaegerSpanExporterProperties {

  private boolean enabled = true;
  private String serviceName = DEFAULT_SERVICE_NAME;
  private String endpoint = DEFAULT_ENDPOINT;
  private Duration spanTimeout = Duration.ofMillis(DEFAULT_DEADLINE_MS);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public Duration getSpanTimeout() {
    return spanTimeout;
  }

  public void setSpanTimeout(Duration spanTimeout) {
    this.spanTimeout = spanTimeout;
  }
}
