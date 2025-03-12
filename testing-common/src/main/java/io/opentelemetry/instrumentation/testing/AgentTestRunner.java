/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.VerifyException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.LoggerUtils;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link InstrumentationTestRunner} that delegates most of its calls to the
 * OpenTelemetry Javaagent that this process runs with. It uses the {@link
 * AgentTestingExporterAccess} bridge class to retrieve exported traces and metrics data from the
 * agent class loader.
 */
public final class AgentTestRunner extends InstrumentationTestRunner {
  static {
    try {
      LoggerUtils.setLevel(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), Level.WARN);
      LoggerUtils.setLevel(LoggerFactory.getLogger("io.opentelemetry"), Level.DEBUG);
    } catch (NoClassDefFoundError e) {
      // this happens when excluding logback in order to test slf4j -> log4j2
    }
  }

  private static final AgentTestRunner INSTANCE = new AgentTestRunner();

  public static InstrumentationTestRunner instance() {
    return INSTANCE;
  }

  private AgentTestRunner() {
    super(GlobalOpenTelemetry.get());
  }

  @Override
  public void beforeTestClass() {
    TestAgentListenerAccess.reset();
  }

  @Override
  public void afterTestClass() throws IOException {
    // Cleanup before assertion.
    assert TestAgentListenerAccess.getInstrumentationErrorCount() == 0
        : TestAgentListenerAccess.getInstrumentationErrorCount()
            + " Instrumentation errors during test";
    int adviceFailureCount = TestAgentListenerAccess.getAndResetAdviceFailureCount();
    assert adviceFailureCount == 0 : adviceFailureCount + " Advice failures during test";
    int muzzleFailureCount = TestAgentListenerAccess.getAndResetMuzzleFailureCount();
    assert muzzleFailureCount == 0 : muzzleFailureCount + " Muzzle failures during test";

    // Generates emitted_telemetry.yaml file with all emitted telemetry to be used
    // by the instrumentation-docs Doc generator.
    //    if (Boolean.getBoolean("collectMetadata")) {
    writeInstrumentationScopesToFile();
    //    }

    // additional library ignores are ignored during tests, because they can make it really
    // confusing for contributors wondering why their instrumentation is not applied
    //
    // but we then need to make sure that the additional library ignores won't then silently prevent
    // the instrumentation from being applied in real life outside of these tests
    assert TestAgentListenerAccess.getIgnoredButTransformedClassNames().isEmpty()
        : "Transformed classes match global libraries ignore matcher: "
            + TestAgentListenerAccess.getIgnoredButTransformedClassNames();
  }

  public void writeInstrumentationScopesToFile() throws IOException {

    if (instrumentationScope == null) {
      return;
    }

    URL resource = this.getClass().getClassLoader().getResource("");
    if (resource == null) {
      return;
    }

    String path = Paths.get(resource.getPath()).toString();
    String instrumentationPath = extractInstrumentationPath(path);

    if (instrumentationPath == null) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    String tmpFileLocation = ".telemetry";
    Path telemetryDir = Paths.get(instrumentationPath, tmpFileLocation);

    // Create the .telemetry directory if it doesn't exist
    try {
      Files.createDirectories(telemetryDir);
    } catch (FileAlreadyExistsException e) {
      // Directory already exists, no action needed
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

    Path metricsPath =
        Paths.get(instrumentationPath, tmpFileLocation, "metrics-" + UUID.randomUUID() + ".yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(metricsPath.toFile().toPath(), UTF_8)) {

      if (!metrics.isEmpty()) {
        writer.write("metrics:\n");
        for (MetricData metric : metrics.values()) {
          writer.write("  - name: " + metric.getName() + "\n");
          writer.write("    description: " + metric.getDescription() + "\n");
          writer.write("    type: " + metric.getType().toString() + "\n");
          writer.write("    unit: " + metric.getUnit() + "\n");
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

  private static final Pattern pattern =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent/|/library/)");

  private static String extractInstrumentationPath(String path) {

    Matcher matcher = pattern.matcher(path);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  @Override
  public void clearAllExportedData() {
    AgentTestingExporterAccess.reset();
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    return GlobalOpenTelemetry.get();
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return AgentTestingExporterAccess.getExportedSpans();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    return AgentTestingExporterAccess.getExportedMetrics();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    return AgentTestingExporterAccess.getExportedLogRecords();
  }

  @Override
  public boolean forceFlushCalled() {
    return AgentTestingExporterAccess.forceFlushCalled();
  }
}
