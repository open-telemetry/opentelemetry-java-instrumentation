/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.MetricsReporterList;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetrySupplier;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;

public final class KafkaMetricsUtil {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  @SuppressWarnings("unchecked")
  public static void enhanceConfig(Map<? super String, Object> config) {
    // skip enhancing configuration when we have already enhanced it
    if (config.get(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME)
        != null) {
      return;
    }
    config.merge(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        MetricsReporterList.singletonList(OpenTelemetryMetricsReporter.class),
        (class1, class2) -> {
          // class1 is either a class name or List of class names or classes
          if (class1 instanceof List) {
            List<Object> result = new MetricsReporterList<>();
            result.addAll((List<Object>) class1);
            result.addAll((List<Object>) class2);
            return result;
          } else if (class1 instanceof String) {
            String className1 = (String) class1;
            if (className1.isEmpty()) {
              return class2;
            }
          }
          List<Object> result = new MetricsReporterList<>();
          result.add(class1);
          result.addAll((List<Object>) class2);
          return result;
        });
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(GlobalOpenTelemetry.get()));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        INSTRUMENTATION_NAME);
  }

  private KafkaMetricsUtil() {}
}
