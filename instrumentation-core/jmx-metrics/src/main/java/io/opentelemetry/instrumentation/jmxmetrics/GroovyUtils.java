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

package io.opentelemetry.instrumentation.jmxmetrics;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.common.Labels.Builder;
import io.opentelemetry.exporters.inmemory.InMemoryMetricExporter;
import io.opentelemetry.exporters.logging.LoggingMetricExporter;
import io.opentelemetry.exporters.otlp.OtlpGrpcMetricExporter;
import io.opentelemetry.metrics.DoubleCounter;
import io.opentelemetry.metrics.DoubleUpDownCounter;
import io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.metrics.LongCounter;
import io.opentelemetry.metrics.LongUpDownCounter;
import io.opentelemetry.metrics.LongValueRecorder;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import java.util.Map;

public class GroovyUtils {

  public Meter meter;
  public MetricExporter exporter;

  /**
   * A central context for creating and exporting metrics, to be used by groovy scripts via {@link
   * io.opentelemetry.instrumentation.jmxmetrics.OtelHelper}.
   *
   * @param config - used to establish exporter type (logging by default) and connection info
   * @param instrumentationName - meter's instrumentationName
   * @param instrumentationVersion - meter's instrumentationVersion
   */
  public GroovyUtils(JmxConfig config, String instrumentationName, String instrumentationVersion) {
    meter = OpenTelemetry.getMeter(instrumentationName, instrumentationVersion);

    switch (config.exporterType.toLowerCase()) {
      case "otlp":
        exporter = OtlpGrpcMetricExporter.newBuilder().setEndpoint(config.exporterEndpoint).build();
        break;
      case "inmemory":
        exporter = InMemoryMetricExporter.create();
        break;
      default:
        exporter = new LoggingMetricExporter();
        break;
    }
  }

  /**
   * Configures with default meter identifiers.
   *
   * @param config - used to establish exporter type (logging by default) and connection info
   */
  public GroovyUtils(JmxConfig config) {
    this(config, "jmx-metrics", "0.0.1");
  }

  /** Will collect all metrics from OpenTelemetrySdk and export via configured exporter. */
  public void exportMetrics() {
    Collection<MetricData> md =
        OpenTelemetrySdk.getMeterProvider().getMetricProducer().collectAllMetrics();
    exporter.export(md);
  }

  private static Labels mapToLabels(Map<String, String> labelMap) {
    Builder labels = new Builder();
    if (labelMap != null) {
      for (Map.Entry<String, String> kv : labelMap.entrySet()) {
        labels.setLabel(kv.getKey(), kv.getValue());
      }
    }
    return labels.build();
  }

  /**
   * Build or retrieve previously registered {@link DoubleCounter}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link DoubleCounter}
   */
  public DoubleCounter getDoubleCounter(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .doubleCounterBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }

  /**
   * Build or retrieve previously registered {@link LongCounter}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link LongCounter}
   */
  public LongCounter getLongCounter(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .longCounterBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }

  /**
   * Build or retrieve previously registered {@link DoubleUpDownCounter}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link DoubleUpDownCounter}
   */
  public DoubleUpDownCounter getDoubleUpDownCounter(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .doubleUpDownCounterBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }

  /**
   * Build or retrieve previously registered {@link LongUpDownCounter}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link LongUpDownCounter}
   */
  public LongUpDownCounter getLongUpDownCounter(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .longUpDownCounterBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }

  /**
   * Build or retrieve previously registered {@link DoubleValueRecorder}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link DoubleValueRecorder}
   */
  public DoubleValueRecorder getDoubleValueRecorder(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .doubleValueRecorderBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }

  /**
   * Build or retrieve previously registered {@link LongValueRecorder}.
   *
   * @param name - metric name
   * @param description metric description
   * @param unit - metric unit
   * @param constantLabels - metric descriptor's constant labels
   * @return new or memoized {@link LongValueRecorder}
   */
  public LongValueRecorder getLongValueRecorder(
      String name, String description, String unit, Map<String, String> constantLabels) {
    Labels labels = mapToLabels(constantLabels);
    return meter
        .longValueRecorderBuilder(name)
        .setDescription(description)
        .setUnit(unit)
        .setConstantLabels(labels)
        .build();
  }
}
