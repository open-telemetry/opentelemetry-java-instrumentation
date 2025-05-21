/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

  // thread-safe after initialization
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  public static void writeTelemetryToFiles(String path, Map<String, MetricData> metrics)
      throws IOException {

    String moduleRoot = extractInstrumentationPath(path);
    writeMetricData(moduleRoot, metrics);
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

  private static void writeMetricData(String moduleRoot, Map<String, MetricData> metrics)
      throws IOException {
    if (metrics.isEmpty()) {
      return;
    }

    Path output = Paths.get(moduleRoot, TMP_DIR, "metrics-" + UUID.randomUUID() + ".yaml");

    Map<String, Object> root = new LinkedHashMap<>();
    List<Map<String, Object>> metricList = new ArrayList<>();

    for (MetricData metric : metrics.values()) {
      Map<String, Object> metricNode = new LinkedHashMap<>();
      metricNode.put("name", metric.getName());
      metricNode.put("description", metric.getDescription());
      metricNode.put("type", metric.getType().toString());
      metricNode.put("unit", sanitizeUnit(metric.getUnit()));

      List<Map<String, String>> attributes = new ArrayList<>();
      metric.getData().getPoints().stream()
          .findFirst()
          .ifPresent(
              p ->
                  p.getAttributes()
                      .forEach(
                          (key, value) -> {
                            Map<String, String> attr = new LinkedHashMap<>();
                            attr.put("name", key.getKey());
                            attr.put("type", key.getType().toString());
                            attributes.add(attr);
                          }));

      metricNode.put("attributes", attributes);
      metricList.add(metricNode);
    }

    root.put("metrics", metricList);

    try (BufferedWriter writer = Files.newBufferedWriter(output, UTF_8)) {
      YAML_MAPPER.writeValue(writer, root);
    }
  }

  private static String sanitizeUnit(String unit) {
    return unit == null ? null : unit.replace("{", "").replace("}", "");
  }

  private MetaDataCollector() {}
}
