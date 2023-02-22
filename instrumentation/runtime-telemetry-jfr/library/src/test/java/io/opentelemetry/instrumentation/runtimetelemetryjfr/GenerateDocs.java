/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Called from {@code ./gradlew generateDocs} to generate the markdown table in README.md. */
public class GenerateDocs {

  private static final Logger LOGGER = Logger.getLogger(GenerateDocs.class.getName());

  private static final String JFR_README_PATH_KEY = "jfr.readme.path";
  private static final String START = "<!-- generateDocsStart -->";
  private static final String END = "<!-- generateDocsEnd -->";
  private static final Pattern PATTERN = Pattern.compile(START + ".*" + END, Pattern.DOTALL);

  private GenerateDocs() {}

  public static void main(String[] args) throws Exception {
    // Suppress info level logs
    Logger.getLogger(JfrTelemetry.class.getName()).setLevel(Level.WARNING);

    String jfrReadmePath = System.getProperty(JFR_README_PATH_KEY);
    if (jfrReadmePath == null) {
      throw new IllegalStateException(JFR_README_PATH_KEY + " is required");
    }

    LOGGER.info("Generating JFR docs. Writing to " + jfrReadmePath);
    String markdownTable = generateMarkdownTable();
    LOGGER.info("Markdown table: " + System.lineSeparator() + markdownTable);
    writeReadme(markdownTable, jfrReadmePath);
    LOGGER.info("Done");
  }

  private static String generateMarkdownTable() throws InterruptedException {
    // Create new JfrTelemetry for each JfrFeature
    Map<JfrFeature, JfrTelemetryWithFeature> map = new HashMap<>();
    for (JfrFeature feature : JfrFeature.values()) {
      InMemoryMetricReader reader = InMemoryMetricReader.createDelta();
      OpenTelemetrySdk sdk =
          OpenTelemetrySdk.builder()
              .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
              .build();
      map.put(
          feature,
          new JfrTelemetryWithFeature(
              JfrTelemetry.builder(sdk).disableAllFeatures().enableFeature(feature).build(),
              sdk,
              reader));
    }

    // Exercise JVM to produce various JFR events
    System.gc();
    executeDummyNetworkRequest("https://google.com");
    Thread.sleep(2000);

    // Build table, shut everything down
    StringBuilder table =
        new StringBuilder("| JfrFeature | Default Enabled | Metrics |")
            .append(System.lineSeparator())
            .append("|---|---|---|")
            .append(System.lineSeparator());
    for (JfrFeature feature : JfrFeature.values()) {
      JfrTelemetryWithFeature jfrTelemetryWithFeature = map.get(feature);
      String metricCol =
          jfrTelemetryWithFeature.reader.collectAllMetrics().stream()
              .map(MetricData::getName)
              .collect(toSet())
              .stream()
              .sorted()
              .map(s -> "`" + s + "`")
              .collect(joining(", "));
      table
          .append("| ")
          .append(feature.name())
          .append(" | ")
          .append(feature.isDefaultEnabled())
          .append(" | ")
          .append(metricCol)
          .append(" |")
          .append(System.lineSeparator());
      jfrTelemetryWithFeature.sdk.getSdkMeterProvider().close();
      jfrTelemetryWithFeature.jfrTelemetry.close();
    }

    return table.toString();
  }

  private static class JfrTelemetryWithFeature {
    private final JfrTelemetry jfrTelemetry;
    private final OpenTelemetrySdk sdk;
    private final InMemoryMetricReader reader;

    private JfrTelemetryWithFeature(
        JfrTelemetry jfrTelemetry, OpenTelemetrySdk sdk, InMemoryMetricReader reader) {
      this.jfrTelemetry = jfrTelemetry;
      this.sdk = sdk;
      this.reader = reader;
    }
  }

  private static void executeDummyNetworkRequest(String urlString) {
    try {
      URL url = new URL(urlString);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.getResponseCode();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to execute request", e);
    }
  }

  private static void writeReadme(String markdownTable, String jfrReadmePath) throws IOException {
    Path path = Paths.get(jfrReadmePath);
    String readmeContent = Files.readString(path);
    readmeContent =
        PATTERN
            .matcher(readmeContent)
            .replaceAll(
                START
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + markdownTable
                    + System.lineSeparator()
                    + END);
    Files.writeString(path, readmeContent);
  }
}
