/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class InternalJfrConfigTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void absentSelectorKeepsJfrDisabled() {
    TestConfig config = new TestConfig();

    RuntimeTelemetry runtimeTelemetry = config.configure();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void starPatternSelectsAll() {
    ensureJfrAvailable();

    TestConfig config = new TestConfig();
    when(config.jfrMetrics.getScalarList("included", String.class)).thenReturn(singletonList("*"));

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics = config.configureJfr();

    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.cpu.longlock", "jvm.memory.allocation");
  }

  // an empty selector is equivalent to no selector at all
  @Test
  void emptySelectorKeepsJfrDisabled() {
    TestConfig config = new TestConfig();
    when(config.jfrMetrics.getScalarList("included", String.class)).thenReturn(emptyList());
    when(config.jfrMetrics.getScalarList("excluded", String.class)).thenReturn(emptyList());

    RuntimeTelemetry runtimeTelemetry = config.configure();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void exclusionsWinOverShorthandAndLegacySelections() {
    ensureJfrAvailable();

    TestConfig config = new TestConfig();
    when(config.jfrMetrics.getScalarList("included", String.class))
        .thenReturn(singletonList("jvm.cpu.context_switch"));
    when(config.jfrMetrics.getScalarList("excluded", String.class))
        .thenReturn(asList("jvm.cpu.longlock", "jvm.class.count"));
    when(config.runtimeTelemetry.getBoolean("emit_experimental_jfr_metrics/development", false))
        .thenReturn(true);
    when(config.runtimeTelemetry.getBoolean("prefer_jfr/development", false)).thenReturn(true);
    when(config.runtimeTelemetryJava17.getBoolean("enabled", false)).thenReturn(true);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics = config.configureJfr();

    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.cpu.context_switch", "jvm.memory.allocation")
        .doesNotContain("jvm.cpu.longlock", "jvm.class.count");
  }

  private static void ensureJfrAvailable() {
    try {
      Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
  }

  private final class TestConfig {
    private final ExtendedOpenTelemetry openTelemetry =
        mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    private final DeclarativeConfigProperties runtimeTelemetry =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    private final DeclarativeConfigProperties runtimeTelemetryJava17 =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    private final DeclarativeConfigProperties jfrMetrics =
        runtimeTelemetry.get("jfr_metrics/development");

    private TestConfig() {
      when(openTelemetry.getInstrumentationConfig("runtime_telemetry"))
          .thenReturn(runtimeTelemetry);
      when(openTelemetry.getInstrumentationConfig("runtime_telemetry_java17"))
          .thenReturn(runtimeTelemetryJava17);
      when(runtimeTelemetry.getBoolean("enabled", true)).thenReturn(true);
      when(jfrMetrics.getScalarList("included", String.class)).thenReturn(null);
      when(jfrMetrics.getScalarList("excluded", String.class)).thenReturn(null);
    }

    private RuntimeTelemetry configure() {
      RuntimeTelemetry runtimeTelemetry = Internal.configure(openTelemetry, true);
      assertThat(runtimeTelemetry).isNotNull();
      return runtimeTelemetry;
    }

    private JfrConfig.JfrRuntimeMetrics configureJfr() {
      RuntimeTelemetry runtimeTelemetry = configure();
      cleanup.deferCleanup(runtimeTelemetry);
      assertThat(runtimeTelemetry.getJfrTelemetry())
          .isInstanceOf(JfrConfig.JfrRuntimeMetrics.class);
      return (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    }
  }
}
