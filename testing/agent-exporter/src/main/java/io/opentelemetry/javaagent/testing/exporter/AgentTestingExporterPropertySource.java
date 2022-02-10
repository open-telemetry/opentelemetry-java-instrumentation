/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;
import java.util.HashMap;
import java.util.Map;

@AutoService(ConfigPropertySource.class)
public class AgentTestingExporterPropertySource implements ConfigPropertySource {
  @Override
  public Map<String, String> getProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.traces.exporter", "none");
    return properties;
  }
}
