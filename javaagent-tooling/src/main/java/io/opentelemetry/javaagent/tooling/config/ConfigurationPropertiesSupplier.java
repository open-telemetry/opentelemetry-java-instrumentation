/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoService(AutoConfigurationCustomizerProvider.class)
public final class ConfigurationPropertiesSupplier implements AutoConfigurationCustomizerProvider {

  private static final List<String> PROTOCOL_ENV_VARS = new ArrayList<>(4);
  private static final List<String> PROTOCOL_SYSTEM_PROPERTIES = new ArrayList<>(4);

  static {
    PROTOCOL_ENV_VARS.add("OTEL_EXPORTER_OTLP_PROTOCOL");
    PROTOCOL_ENV_VARS.add("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
    PROTOCOL_ENV_VARS.add("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
    PROTOCOL_ENV_VARS.add("OTEL_EXPORTER_OTLP_LOGS_PROTOCOL");

    PROTOCOL_SYSTEM_PROPERTIES.add("otel.exporter.otlp.protocol");
    PROTOCOL_SYSTEM_PROPERTIES.add("otel.exporter.otlp.traces.protocol");
    PROTOCOL_SYSTEM_PROPERTIES.add("otel.exporter.otlp.metrics.protocol");
    PROTOCOL_SYSTEM_PROPERTIES.add("otel.exporter.otlp.logs.protocol");
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(ConfigurationFile::getProperties);

    // we honour customer's choice of protocol, but if they didn't specify it, we default to
    // http/protobuf
    for (int i = 0; i < PROTOCOL_ENV_VARS.size(); i++) {
      String envVar = PROTOCOL_ENV_VARS.get(i);
      String systemProperty = PROTOCOL_SYSTEM_PROPERTIES.get(i);
      if (System.getenv(envVar) != null || System.getProperty(systemProperty) != null) {
        // if any of the protocol env var or system property is set by customers, we don't want to
        // override it
        return;
      }
      autoConfiguration.addPropertiesSupplier(
          () -> Collections.singletonMap(systemProperty, "http/protobuf"));
    }
  }

  @Override
  public int order() {
    // make sure it runs after all the user-provided customizers
    return Integer.MAX_VALUE;
  }
}
