package com.example.javaagent;

import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;
import java.util.Map;

/**
 * {@link ConfigPropertySource} is an SPI provided by OpenTelemetry Java instrumentation agent. By
 * implementing it custom distributions can supply their own default configuration. The
 * configuration priority, from highest to lowest is: system properties -> environment variables ->
 * configuration file -> PropertySource SPI -> hard-coded defaults
 */
public class DemoPropertySource implements ConfigPropertySource {

  @Override
  public Map<String, String> getProperties() {
    return Map.of(
        "otel.exporter.otlp.endpoint", "http://backend:8080",
        "otel.exporter.otlp.insecure", "true",
        "otel.config.max.attrs", "16");
  }
}
