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
import io.opentelemetry.instrumentation.test.utils.LoggerUtils;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
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
    if (Boolean.getBoolean("collectMetadata")) {
      writeInstrumentationScopesToFile(instrumentationScopes);
    }

    // additional library ignores are ignored during tests, because they can make it really
    // confusing for contributors wondering why their instrumentation is not applied
    //
    // but we then need to make sure that the additional library ignores won't then silently prevent
    // the instrumentation from being applied in real life outside of these tests
    assert TestAgentListenerAccess.getIgnoredButTransformedClassNames().isEmpty()
        : "Transformed classes match global libraries ignore matcher: "
            + TestAgentListenerAccess.getIgnoredButTransformedClassNames();
  }

  public void writeInstrumentationScopesToFile(Set<InstrumentationScopeInfo> instrumentationScopes)
      throws IOException {

    if (instrumentationScopes == null) {
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

    Path outputPath = Paths.get(instrumentationPath, "emitted_telemetry.yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(outputPath.toFile().toPath(), UTF_8)) {
      writer.write("scope:\n");
      for (InstrumentationScopeInfo scope : instrumentationScopes) {
        writer.write("  name: " + scope.getName() + "\n");
        writer.write("  version: " + scope.getVersion() + "\n");
        writer.write("  schemaUrl: " + scope.getSchemaUrl() + "\n");
        if (scope.getAttributes() == null) {
          writer.write("  attributes: {}\n");
        } else {
          writer.write("  attributes:\n");
          scope
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
