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
 * Loads opentelemetry.trace.tracer.name and opentelemetry.trace.tracer.loggingExporterIsEnabled
 * from application.properties <br>
 * Sets the default values if the configurations do not exist
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.tracer")
public final class TracerProperties {

  private String name = "otel-spring-tracer";
  private boolean loggingExporterIsEnabled = true;

  public String getName() {
    return name;
  }

  public void setName(String tracerName) {
    this.name = tracerName;
  }

  public boolean isLoggingExporterIsEnabled() {
    return loggingExporterIsEnabled;
  }

  public void setLoggingExporterIsEnabled(boolean loggingExporterIsEnabled) {
    this.loggingExporterIsEnabled = loggingExporterIsEnabled;
  }
}
