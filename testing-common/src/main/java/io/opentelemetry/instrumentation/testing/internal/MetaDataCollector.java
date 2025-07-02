/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used in the {@link io.opentelemetry.instrumentation.testing.AgentTestRunner} to write telemetry
 * to metadata files within a .telemetry directory in each instrumentation module. This information
 * is then parsed and used to generate the instrumentation-list.yaml file.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetaDataCollector {

  private static final String TMP_DIR = ".telemetry";
  private static final Pattern MODULE_PATTERN =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent/|/library/)");

  public static void writeTelemetryToFiles(
      String path,
      Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope,
      Map<InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
          spansByScopeAndKind)
      throws IOException {

    String moduleRoot = extractInstrumentationPath(path);
    writeMetricData(moduleRoot, metricsByScope);
    writeSpanData(moduleRoot, spansByScopeAndKind);
  }

  private static String extractInstrumentationPath(String path) {
    Matcher matcher = MODULE_PATTERN.matcher(path);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    String instrumentationPath = matcher.group(1);
    Path telemetryDir = Paths.get(instrumentationPath, TMP_DIR);

    try {
      Files.createDirectories(telemetryDir);
    } catch (FileAlreadyExistsException ignored) {
      // Directory already exists; nothing to do
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    return instrumentationPath;
  }

  private static void writeSpanData(
      String instrumentationPath,
      Map<InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
          spansByScopeAndKind)
      throws IOException {

    if (spansByScopeAndKind.isEmpty()) {
      return;
    }

    Path spansPath =
        Paths.get(instrumentationPath, TMP_DIR, "spans-" + UUID.randomUUID() + ".yaml");

    try (BufferedWriter writer = Files.newBufferedWriter(spansPath.toFile().toPath(), UTF_8)) {
      String config = System.getProperty("metaDataConfig");
      String when = "default";
      if (config != null && !config.isEmpty()) {
        when = config;
      }

      writer.write("when: " + when + "\n");

      writer.write("spans_by_scope:\n");

      for (Map.Entry<
              InstrumentationScopeInfo,
              Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
          entry : spansByScopeAndKind.entrySet()) {
        InstrumentationScopeInfo scope = entry.getKey();
        Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>> spansByKind =
            entry.getValue();

        writer.write("  - scope: " + scope.getName() + "\n");
        writer.write("    spans:\n");

        for (Map.Entry<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>> kindEntry :
            spansByKind.entrySet()) {
          SpanKind spanKind = kindEntry.getKey();
          Map<InternalAttributeKeyImpl<?>, AttributeType> attributes = kindEntry.getValue();

          writer.write("      - span_kind: " + spanKind.toString() + "\n");
          writer.write("        attributes:\n");
          attributes.forEach(
              (key, value) -> {
                try {
                  writer.write("          - name: " + key.getKey() + "\n");
                  writer.write("            type: " + key.getType().toString() + "\n");
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              });
        }
      }
    }
  }

  private static void writeMetricData(
      String instrumentationPath,
      Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope)
      throws IOException {

    if (metricsByScope.isEmpty()) {
      return;
    }

    Path metricsPath =
        Paths.get(instrumentationPath, TMP_DIR, "metrics-" + UUID.randomUUID() + ".yaml");

    try (BufferedWriter writer = Files.newBufferedWriter(metricsPath.toFile().toPath(), UTF_8)) {
      String config = System.getProperty("metaDataConfig");
      String when = "default";
      if (config != null && !config.isEmpty()) {
        when = config;
      }

      writer.write("when: " + when + "\n");

      writer.write("metrics_by_scope:\n");

      for (Map.Entry<InstrumentationScopeInfo, Map<String, MetricData>> entry :
          metricsByScope.entrySet()) {
        InstrumentationScopeInfo scope = entry.getKey();
        Map<String, MetricData> metrics = entry.getValue();

        writer.write("  - scope: " + scope.getName() + "\n");
        writer.write("    metrics:\n");

        for (MetricData metric : metrics.values()) {
          writer.write("      - name: " + metric.getName() + "\n");
          writer.write("        description: " + metric.getDescription() + "\n");
          writer.write("        type: " + metric.getType().toString() + "\n");
          writer.write("        unit: " + sanitizeUnit(metric.getUnit()) + "\n");
          writer.write("        attributes: \n");
          metric.getData().getPoints().stream()
              .findFirst()
              .get()
              .getAttributes()
              .forEach(
                  (key, value) -> {
                    try {
                      writer.write("          - name: " + key.getKey() + "\n");
                      writer.write("            type: " + key.getType().toString() + "\n");
                    } catch (IOException e) {
                      throw new IllegalStateException(e);
                    }
                  });
        }
      }
    }
  }

  private static String sanitizeUnit(String unit) {
    return unit == null ? null : unit.replace("{", "").replace("}", "");
  }

  private MetaDataCollector() {}
}
