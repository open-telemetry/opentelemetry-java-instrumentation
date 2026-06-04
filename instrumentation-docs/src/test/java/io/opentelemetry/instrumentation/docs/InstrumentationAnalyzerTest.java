/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.parsers.ModuleParser;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class InstrumentationAnalyzerTest {

  @Test
  void testConvertToInstrumentationModule() {
    List<InstrumentationPath> paths =
        asList(
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/library",
                "log4j",
                "log4j",
                InstrumentationType.LIBRARY),
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/javaagent",
                "log4j",
                "log4j",
                InstrumentationType.JAVAAGENT),
            new InstrumentationPath(
                "spring-web",
                "instrumentation/spring/spring-web/library",
                "spring",
                "spring",
                InstrumentationType.LIBRARY));

    List<InstrumentationModule> modules = ModuleParser.convertToModules("test", paths);

    assertThat(modules.size()).isEqualTo(2);

    InstrumentationModule log4jModule =
        modules.stream()
            .filter(e -> e.getInstrumentationName().equals("log4j-appender-2.17"))
            .findFirst()
            .orElse(null);

    assertThat(log4jModule).isNotNull();
    assertThat(log4jModule.getNamespace()).isEqualTo("log4j");
    assertThat(log4jModule.getGroup()).isEqualTo("log4j");
    assertThat(log4jModule.getSrcPath()).isEqualTo("instrumentation/log4j/log4j-appender-2.17");
    assertThat(log4jModule.getScopeInfo().getName())
        .isEqualTo("io.opentelemetry.log4j-appender-2.17");

    InstrumentationModule springModule =
        modules.stream()
            .filter(e -> e.getInstrumentationName().equals("spring-web"))
            .findFirst()
            .orElse(null);

    assertThat(springModule).isNotNull();
    assertThat(springModule.getNamespace()).isEqualTo("spring");
    assertThat(springModule.getGroup()).isEqualTo("spring");
    assertThat(springModule.getSrcPath()).isEqualTo("instrumentation/spring/spring-web");
    assertThat(springModule.getScopeInfo().getName()).isEqualTo("io.opentelemetry.spring-web");
  }

  @Test
  void testModuleConverterCreatesUniqueModules() {
    List<InstrumentationPath> paths =
        asList(
            new InstrumentationPath(
                "same-name",
                "instrumentation/test1/same-name/library",
                "namespace1",
                "group1",
                InstrumentationType.LIBRARY),
            new InstrumentationPath(
                "same-name",
                "instrumentation/test2/same-name/library",
                "namespace2",
                "group2",
                InstrumentationType.LIBRARY));

    List<InstrumentationModule> modules = ModuleParser.convertToModules("test", paths);

    // Should create 2 separate modules because they have different group/namespace combinations
    assertThat(modules.size()).isEqualTo(2);

    assertThat(
            modules.stream()
                .anyMatch(
                    m -> m.getGroup().equals("group1") && m.getNamespace().equals("namespace1")))
        .isTrue();
    assertThat(
            modules.stream()
                .anyMatch(
                    m -> m.getGroup().equals("group2") && m.getNamespace().equals("namespace2")))
        .isTrue();
  }

  @Test
  void testAnalyzeAppliesEmittedScopeBeforeFilteringTelemetry(@TempDir Path tempDir)
      throws IOException {
    Path instrumentationDir = tempDir.resolve("instrumentation").resolve("oshi-5.0");
    Files.createDirectories(instrumentationDir.resolve("javaagent"));
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    Files.writeString(
        telemetryDir.resolve("scope-abc123.yaml"),
        """
        scopes:
          - name: io.opentelemetry.sdk.metrics
            version: null
            schemaUrl: null
          - name: io.opentelemetry.oshi
            version: 2.14.0
            schemaUrl: null
        """);
    Files.writeString(
        telemetryDir.resolve("metrics-abc123.yaml"),
        """
        when: default
        metrics_by_scope:
        - scope: io.opentelemetry.oshi
          metrics:
          - name: system.memory.usage
            description: System memory usage
            type: LONG_SUM
            unit: By
            attributes:
            - name: state
              type: STRING
            is_monotonic: false
        """);

    List<InstrumentationModule> modules =
        new InstrumentationAnalyzer(new FileManager(tempDir + "/")).analyze();

    assertThat(modules).hasSize(1);
    InstrumentationModule module = modules.get(0);
    assertThat(module.getInstrumentationName()).isEqualTo("oshi-5.0");
    assertThat(module.getScopeInfo().getName()).isEqualTo("io.opentelemetry.oshi");
    assertThat(module.getMetrics()).containsOnlyKeys("default");
    assertThat(module.getMetrics().get("default"))
        .extracting(EmittedMetrics.Metric::getName)
        .containsExactly("system.memory.usage");
  }
}
