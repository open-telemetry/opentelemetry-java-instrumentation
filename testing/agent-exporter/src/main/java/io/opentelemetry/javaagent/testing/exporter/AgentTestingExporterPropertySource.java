/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigCustomizer;
import java.util.HashMap;
import java.util.Map;

@AutoService(ConfigCustomizer.class)
public class AgentTestingExporterPropertySource implements ConfigCustomizer {

  @Override
  public Map<String, String> defaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.traces.exporter", "none");
    return properties;
  }
}
