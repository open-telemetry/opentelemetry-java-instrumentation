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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for OpenTelemetry Tracer
 *
 * <p>Configures LoggingExporter and sets default tracer name
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.tracer")
public final class TracerProperties {

  private String name = "otel-spring-tracer";
  private boolean loggingExporterEnabled = true;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isLoggingExporterEnabled() {
    return loggingExporterEnabled;
  }

  public void setLoggingExporterEnabled(boolean loggingExporterEnabled) {
    this.loggingExporterEnabled = loggingExporterEnabled;
  }
}
