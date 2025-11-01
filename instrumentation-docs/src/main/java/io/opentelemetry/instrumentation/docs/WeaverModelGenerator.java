/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WeaverModelGenerator {

  private static final Path baseRepoPath = getPath();

  private static Path getPath() {
    String base = System.getProperty("basePath");
    if (base == null || base.isBlank()) {
      return Paths.get(".");
    }
    return Paths.get(base);
  }

  public static void generateWeaverModels(List<InstrumentationModule> modules) throws IOException {
    for (InstrumentationModule module : modules) {
      if (!module.getInstrumentationName().equals("activej-http-6.0")) {
        continue;
      }

      Path moduleSrc = baseRepoPath.resolve(module.getSrcPath());
      Path modelsDir = moduleSrc.resolve("models");
      Files.createDirectories(modelsDir);

      Path signalsPath = modelsDir.resolve("signals.yaml");
      try (BufferedWriter writer = Files.newBufferedWriter(signalsPath, UTF_8)) {
        generateSignals(module, writer);
      }

      Path manifestPath = modelsDir.resolve("registry_manifest.yaml");
      try (BufferedWriter writer = Files.newBufferedWriter(manifestPath, UTF_8)) {
        generateManifest(module, writer);
      }

      Set<String> attributes = getMetricAttributes(module);
      if (!attributes.isEmpty()) {
        Path attributesPath = modelsDir.resolve("attributes.yaml");
        try (BufferedWriter writer = Files.newBufferedWriter(attributesPath, UTF_8)) {
          generateAttributes(module, writer, attributes);
        }
      }
    }
  }

  public static void generateSignals(InstrumentationModule module, BufferedWriter writer)
      throws IOException {
    writer.write("# This file is generated and should not be manually edited.\n");
    writer.write("groups:\n");

    Map<String, List<EmittedMetrics.Metric>> metricsMap = module.getMetrics();
    if (metricsMap != null && metricsMap.get("default") != null) {
      for (EmittedMetrics.Metric metric : metricsMap.get("default")) {
        writer.write("  - id: metric." + quote(metric.getName()) + "\n");
        writer.write("    type: metric\n");
        writer.write("    metric_name: " + quote(metric.getName()) + "\n");
        writer.write("    stability: development\n");
        writer.write("    brief: " + quote(metric.getDescription()) + "\n");
        writer.write("    instrument: " + quote(metric.getType().toLowerCase(Locale.ROOT)) + "\n");
        writer.write("    unit: " + quote(metric.getUnit()) + "\n");
        writer.write("    attributes:\n");
        for (TelemetryAttribute attribute : metric.getAttributes()) {
          writer.write("      - ref: " + quote(attribute.getName()) + "\n");
        }
      }
    }
  }

  public static void generateManifest(InstrumentationModule module, BufferedWriter writer)
      throws IOException {
    writer.write("# This file is generated and should not be manually edited.\n");
    writer.write("name: " + quote(module.getInstrumentationName()) + "\n");
    writer.write(
        "description: " + quote(module.getInstrumentationName() + " Semantic Conventions") + "\n");
    writer.write("semconv_version: 0.1.0\n");
    writer.write("schema_base_url: https://weaver-example.io/schemas/\n");
    writer.write("dependencies:\n");
    writer.write("  - name: otel\n");
    writer.write(
        "    registry_path: https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.34.0.zip[model]");
  }

  public static void generateAttributes(
      InstrumentationModule module, BufferedWriter writer, Set<String> attributes)
      throws IOException {
    writer.write("# This file is generated and should not be manually edited.\n");
    writer.write("groups:\n");
    writer.write("  - id: registry." + module.getInstrumentationName() + "\n");
    writer.write("    type: attribute_group\n");
    writer.write("    display_name: " + module.getResolvedName() + " Attributes\n");
    writer.write(
        "    brief: Attributes captured by " + module.getResolvedName() + " instrumentation.\n");
    writer.write("    attributes:\n");
    for (String attribute : attributes) {
      writer.write("      - ref: " + attribute + "\n");
    }
  }

  /**
   * Get all metric attributes used by the given module. Sorted and deduplicated.
   *
   * @param module the instrumentation module
   * @return set of attribute names
   */
  // visible for testing
  public static Set<String> getMetricAttributes(InstrumentationModule module) {
    Set<String> attributes = new java.util.TreeSet<>();
    if (module.getMetrics() != null && module.getMetrics().get("default") != null) {
      for (EmittedMetrics.Metric metric : module.getMetrics().get("default")) {
        for (TelemetryAttribute attribute : metric.getAttributes()) {
          String name = attribute.getName();
          if (name != null) {
            attributes.add(name);
          }
        }
      }
    }
    return attributes;
  }

  private static String quote(String s) {
    if (s == null) {
      return "\"\"";
    }
    return "\"" + s.replace("\"", "\\\"") + "\"";
  }

  private WeaverModelGenerator() {}
}
