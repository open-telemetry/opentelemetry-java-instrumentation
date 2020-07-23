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

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link OTLPGrpcSpanExporter}
 *
 * <p>Get Exporter Service Name
 *
 * <p>Get Exporter Host Name
 *
 * <p>Get Exporter Port
 *
 * <p>Get max wait time for Collector to process Span Batches
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.otlp")
public final class OtlpGrpcSpanExporterProperties {

  private boolean enabled = true;
  private String serviceName = "unknown";
  private String host = "localhost";
  /** Default end point in {@link OTLPGrpcSpanExporter.OTEL_OTLP_ENDPOINT} */
  private int port = 14250;

  private Duration deadline = Duration.ofSeconds(1);

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

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public Duration getDeadline() {
    return deadline;
  }

  public void setDeadline(Duration deadline) {
    this.deadline = deadline;
  }
}
