/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.VerifyException;
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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used in the {@link io.opentelemetry.instrumentation.testing.AgentTestRunner} to write telemetry
 * to metadata files within a .telemetry directory in each instrumentation module. This information
 * is then parsed and used to create the instrumentation list.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetaDataCollector {
  private static final String tmpFileLocation = ".telemetry";
  private static final Pattern pattern =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent/|/library/)");

  public static void writeTelemetryToFiles(
      String path,
      InstrumentationScopeInfo instrumentationScope,
      Set<SpanKind> spanKinds,
      Map<String, String> attributeKeys,
      Map<String, MetricData> metrics)
      throws IOException {
    String instrumentationPath = extractInstrumentationPath(path);

    writeSpanData(instrumentationPath, spanKinds, attributeKeys);
    writeMetricData(instrumentationPath, metrics);
    writeScopeData(instrumentationPath, instrumentationScope);
  }

  private static String extractInstrumentationPath(String path) {
    String instrumentationPath = null;
    Matcher matcher = pattern.matcher(path);
    if (matcher.find()) {
      instrumentationPath = matcher.group(1);
    }

    if (instrumentationPath == null) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    Path telemetryDir = Paths.get(instrumentationPath, tmpFileLocation);

    try {
      Files.createDirectories(telemetryDir);
    } catch (FileAlreadyExistsException e) {
      // Directory already exists, no action needed
    } catch (IOException e) {
      throw new VerifyException(e);
    }

    return instrumentationPath;
  }

  private static void writeMetricData(String instrumentationPath, Map<String, MetricData> metrics)
      throws IOException {

    if (metrics.isEmpty()) {
      return;
    }

    Path metricsPath =
        Paths.get(instrumentationPath, tmpFileLocation, "metrics-" + UUID.randomUUID() + ".yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(metricsPath.toFile().toPath(), UTF_8)) {

      if (!metrics.isEmpty()) {
        writer.write("metrics:\n");
        for (MetricData metric : metrics.values()) {
          writer.write("  - name: " + metric.getName() + "\n");
          writer.write("    description: " + metric.getDescription() + "\n");
          writer.write("    type: " + metric.getType().toString() + "\n");
          writer.write("    unit: " + sanitizeUnit(metric.getUnit()) + "\n");
          writer.write("    attributes: \n");
          metric.getData().getPoints().stream()
              .findFirst()
              .get()
              .getAttributes()
              .forEach(
                  (key, value) -> {
                    try {
                      writer.write("      - name: " + key.getKey() + "\n");
                      writer.write("        type: " + key.getType().toString() + "\n");
                    } catch (IOException e) {
                      throw new VerifyException(e);
                    }
                  });
        }
      }
    }
  }

  private static String sanitizeUnit(String type) {
    if (type == null) {
      return null;
    }

    return type.replace("{", "").replace("}", "");
  }

  private static void writeSpanData(
      String instrumentationPath, Set<SpanKind> spanKinds, Map<String, String> attributeKeys)
      throws IOException {

    if (spanKinds == null && attributeKeys.isEmpty()) {
      return;
    }

    Path spansPath =
        Paths.get(instrumentationPath, tmpFileLocation, "spans-" + UUID.randomUUID() + ".yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(spansPath.toFile().toPath(), UTF_8)) {

      if (spanKinds != null) {
        writer.write("span_kinds:\n");
        for (SpanKind spanKind : spanKinds) {
          writer.write("  - " + spanKind + "\n");
        }
      }

      if (!attributeKeys.isEmpty()) {
        writer.write("attributes:\n");
        for (Map.Entry<String, String> entry : attributeKeys.entrySet()) {
          writer.write("  - name: " + entry.getKey() + "\n");
          writer.write("    type: " + entry.getValue() + "\n");
        }
      }
    }
  }

  private static void writeScopeData(
      String instrumentationPath, InstrumentationScopeInfo instrumentationScope)
      throws IOException {

    if (instrumentationScope == null) {
      return;
    }

    Path outputPath = Paths.get(instrumentationPath, tmpFileLocation, "scope.yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(outputPath.toFile().toPath(), UTF_8)) {
      writer.write("scope:\n");
      writer.write("  name: " + instrumentationScope.getName() + "\n");
      writer.write("  version: " + instrumentationScope.getVersion() + "\n");
      writer.write("  schemaUrl: " + instrumentationScope.getSchemaUrl() + "\n");
      if (instrumentationScope.getAttributes() == null) {
        writer.write("  attributes: {}\n");
      } else {
        writer.write("  attributes:\n");
        instrumentationScope
            .getAttributes()
            .forEach(
                (key, value) -> {
                  try {
                    writer.write("      " + key + ": " + value + "\n");
                  } catch (IOException e) {
                    throw new VerifyException(e);
                  }
                });
      }
    }
  }

  private MetaDataCollector() {}
}
