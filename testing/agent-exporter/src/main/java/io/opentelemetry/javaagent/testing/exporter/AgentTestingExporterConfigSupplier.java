/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTestingExporterConfigSupplier implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(AgentTestingExporterConfigSupplier::getTestProperties);
  }

  private static Map<String, String> getTestProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.traces.exporter", "none");
    return properties;
  }
}
