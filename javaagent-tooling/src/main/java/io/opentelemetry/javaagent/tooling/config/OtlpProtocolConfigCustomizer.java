/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class OtlpProtocolConfigCustomizer
    implements Function<ConfigProperties, Map<String, String>> {

  private static final String HTTP_PROTOBUF = "http/protobuf";
  private static final String OTEL_EXPORTER_OTLP_PROTOCOL = "otel.exporter.otlp.protocol";
  private static final String OTEL_EXPORTER_OTLP_TRACES_PROTOCOL =
      "otel.exporter.otlp.traces.protocol";
  private static final String OTEL_EXPORTER_OTLP_METRICS_PROTOCOL =
      "otel.exporter.otlp.metrics.protocol";
  private static final String OTEL_EXPORTER_OTLP_LOGS_PROTOCOL = "otel.exporter.otlp.logs.protocol";

  @Override
  public Map<String, String> apply(ConfigProperties config) {
    Map<String, String> properties = new HashMap<>();
    String otelExporterOtlpProtocol = config.getString(OTEL_EXPORTER_OTLP_PROTOCOL);
    if (otelExporterOtlpProtocol == null) {
      properties.put(OTEL_EXPORTER_OTLP_PROTOCOL, HTTP_PROTOBUF);
    }
    String otelExporterOtlpTracesProtocol = config.getString(OTEL_EXPORTER_OTLP_TRACES_PROTOCOL);
    if (otelExporterOtlpTracesProtocol == null) {
      properties.put(OTEL_EXPORTER_OTLP_TRACES_PROTOCOL, HTTP_PROTOBUF);
    }
    String otelExporterOtlpMetricsProtocol = config.getString(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL);
    if (otelExporterOtlpMetricsProtocol == null) {
      properties.put(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, HTTP_PROTOBUF);
    }
    String otelExporterOtlpLogsProtocol = config.getString(OTEL_EXPORTER_OTLP_LOGS_PROTOCOL);
    if (otelExporterOtlpLogsProtocol == null) {
      properties.put(OTEL_EXPORTER_OTLP_LOGS_PROTOCOL, HTTP_PROTOBUF);
    }
    return properties;
  }
}
