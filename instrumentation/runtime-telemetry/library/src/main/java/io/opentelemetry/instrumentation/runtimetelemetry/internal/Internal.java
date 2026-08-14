/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 *
 * <p>This class provides configuration methods for use by the Java agent, including backward
 * compatibility support for previous configuration options. The backward compatibility methods may
 * be simplified or removed in a future major version (3.0).
 */
public final class Internal {

  private static final Logger logger = Logger.getLogger(Internal.class.getName());

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, IncludeExclude> setJfrMetrics;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setSuppressOverlappingJmxMetrics;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setCaptureGcCause;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setUseLegacyJfrCpuCountMetric;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setJmxInstrumentationName;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setJfrInstrumentationName;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setDisableJmx;

  /** Selects the metrics to source from JFR. */
  public static void setJfrMetrics(RuntimeTelemetryBuilder builder, IncludeExclude selector) {
    if (setJfrMetrics != null) {
      setJfrMetrics.accept(builder, selector);
    }
  }

  public static void internalSetJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, IncludeExclude> callback) {
    Internal.setJfrMetrics = callback;
  }

  /** Sets whether JMX metrics registered by JFR should be suppressed. */
  public static void setSuppressOverlappingJmxMetrics(
      RuntimeTelemetryBuilder builder, boolean suppress) {
    if (setSuppressOverlappingJmxMetrics != null) {
      setSuppressOverlappingJmxMetrics.accept(builder, suppress);
    }
  }

  public static void internalSetSuppressOverlappingJmxMetrics(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setSuppressOverlappingJmxMetrics = callback;
  }

  /**
   * Sets whether the GC cause attribute should be captured on GC duration metrics. GC cause is
   * always captured when emitting stable JVM semantic conventions; otherwise it defaults to {@code
   * false} and can be enabled via {@code capture_gc_cause} for backward compatibility.
   *
   * @param builder the runtime telemetry builder
   * @param captureGcCause {@code true} to capture the GC cause attribute
   */
  // this method will be removed in 3.0 since GC cause will always be captured in that version
  public static void setCaptureGcCause(RuntimeTelemetryBuilder builder, boolean captureGcCause) {
    if (setCaptureGcCause != null) {
      setCaptureGcCause.accept(builder, captureGcCause);
    }
  }

  public static void internalSetCaptureGcCause(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setCaptureGcCause = callback;
  }

  /**
   * Sets whether to use the legacy metric name {@code jvm.cpu.limit} instead of the standard {@code
   * jvm.cpu.count} for the JFR CPU count feature. This is for backward compatibility with the
   * previous runtime-telemetry-java17 module.
   *
   * @param builder the runtime telemetry builder
   * @param useLegacy {@code true} to use the legacy metric name
   */
  public static void setUseLegacyJfrCpuCountMetric(
      RuntimeTelemetryBuilder builder, boolean useLegacy) {
    if (setUseLegacyJfrCpuCountMetric != null) {
      setUseLegacyJfrCpuCountMetric.accept(builder, useLegacy);
    }
  }

  public static void internalSetUseLegacyJfrCpuCountMetric(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setUseLegacyJfrCpuCountMetric = callback;
  }

  /**
   * Sets the instrumentation name to use for JMX metrics. This is used for backward compatibility
   * to preserve the original instrumentation names when using old configuration options.
   *
   * @param builder the runtime telemetry builder
   * @param name the instrumentation name for JMX metrics
   */
  public static void setJmxInstrumentationName(RuntimeTelemetryBuilder builder, String name) {
    if (setJmxInstrumentationName != null) {
      setJmxInstrumentationName.accept(builder, name);
    }
  }

  public static void internalSetJmxInstrumentationName(
      BiConsumer<RuntimeTelemetryBuilder, String> callback) {
    Internal.setJmxInstrumentationName = callback;
  }

  /**
   * Sets the instrumentation name to use for JFR metrics. This is used for backward compatibility
   * to preserve the original instrumentation names when using old configuration options.
   *
   * @param builder the runtime telemetry builder
   * @param name the instrumentation name for JFR metrics
   */
  public static void setJfrInstrumentationName(RuntimeTelemetryBuilder builder, String name) {
    if (setJfrInstrumentationName != null) {
      setJfrInstrumentationName.accept(builder, name);
    }
  }

  public static void internalSetJfrInstrumentationName(
      BiConsumer<RuntimeTelemetryBuilder, String> callback) {
    Internal.setJfrInstrumentationName = callback;
  }

  /**
   * Disables all JMX-based metrics. This is used for backward compatibility with the
   * runtime-telemetry-java17 module's disableAllJmx() method.
   *
   * @param builder the runtime telemetry builder
   * @param disable {@code true} to disable JMX metrics
   */
  public static void setDisableJmx(RuntimeTelemetryBuilder builder, boolean disable) {
    if (setDisableJmx != null) {
      setDisableJmx.accept(builder, disable);
    }
  }

  public static void internalSetDisableJmx(BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setDisableJmx = callback;
  }

  /**
   * Configures and builds a {@link RuntimeTelemetry} instance based on the provided configuration.
   *
   * @param openTelemetry the OpenTelemetry instance
   * @param defaultEnabled whether instrumentation is enabled by default
   * @return the configured RuntimeTelemetry, or null if runtime telemetry is disabled
   */
  @Nullable
  public static RuntimeTelemetry configure(OpenTelemetry openTelemetry, boolean defaultEnabled) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");
    DeclarativeConfigProperties java17Config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry_java17");

    // Determine which configuration is being used
    boolean baseEnabled = config.getBoolean("enabled", defaultEnabled);
    boolean java17Enabled = java17Config.getBoolean("enabled", false);
    boolean java17EnableAll = java17Config.getBoolean("enable_all", false);
    JfrMetricSelection jfrMetricSelection = JfrMetricSelection.create(config);

    if (!baseEnabled && !java17Enabled && !java17EnableAll) {
      return null; // Nothing is enabled
    }

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(openTelemetry);

    // Preserve the deprecated Java 17 scopes and metric sets while honoring explicit exclusions.
    if (java17EnableAll) {
      configureJava17EnableAll(builder, config, jfrMetricSelection);
      return builder.build();
    }
    if (java17Enabled) {
      configureJava17Enabled(builder, jfrMetricSelection);
      return builder.build();
    }

    // New unified config: handles both old java8 settings and new unified options
    if (baseEnabled) {
      configureUnified(builder, config, jfrMetricSelection);
      return builder.build();
    }

    throw new AssertionError("Unreachable: at least one enabled flag must be true");
  }

  private static void configureJava17EnableAll(
      RuntimeTelemetryBuilder builder,
      DeclarativeConfigProperties config,
      JfrMetricSelection jfrMetricSelection) {
    logger.warning(
        "otel.instrumentation.runtime-telemetry-java17.enable-all is deprecated and will be"
            + " removed in 3.0. Use"
            + " otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included=*"
            + " instead.");
    // For backward compatibility: route JMX metrics to java8 scope, JFR metrics to java17 scope
    Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
    Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java17");
    jfrMetricSelection.apply(builder, emptyList(), true);
    Internal.setSuppressOverlappingJmxMetrics(builder, false);
    Internal.setUseLegacyJfrCpuCountMetric(builder, true);

    // Check if base config also has emit_experimental_telemetry enabled (for JMX experimental)
    boolean emitExperimentalTelemetry =
        config.getBoolean("emit_experimental_telemetry/development", false);
    boolean emitExperimentalMetrics =
        config.getBoolean("emit_experimental_metrics/development", false);
    if (emitExperimentalTelemetry || emitExperimentalMetrics) {
      if (emitExperimentalTelemetry) {
        logger.warning(
            "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry is deprecated and"
                + " will be removed in 3.0. Use"
                + " otel.instrumentation.runtime-telemetry.emit-experimental-metrics instead.");
      }
      Experimental.setEmitExperimentalMetrics(builder, true);
    }
  }

  private static void configureJava17Enabled(
      RuntimeTelemetryBuilder builder, JfrMetricSelection jfrMetricSelection) {
    logger.warning(
        "otel.instrumentation.runtime-telemetry-java17.enabled is deprecated and will be"
            + " removed in 3.0. Use"
            + " otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics instead.");
    jfrMetricSelection.apply(
        builder,
        asList(
            "jvm.cpu.context_switch",
            "jvm.cpu.longlock",
            "jvm.memory.allocation",
            "jvm.network.*",
            "jvm.cpu.limit"),
        false);
    Internal.setUseLegacyJfrCpuCountMetric(builder, true);
    // For backward compatibility: OLD java17 module used java8's JMX factory, so JMX -> java8 scope
    Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
    Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java17");
  }

  private static void configureUnified(
      RuntimeTelemetryBuilder builder,
      DeclarativeConfigProperties config,
      JfrMetricSelection jfrMetricSelection) {
    boolean emitExperimentalMetrics =
        config.getBoolean("emit_experimental_metrics/development", false);
    boolean newConfig = emitExperimentalMetrics || jfrMetricSelection.isConfigured();

    if (newConfig) {
      // New unified config: Use new instrumentation name for both JMX and JFR
      Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry");
      Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry");
    } else {
      // Old java8 config: JMX-only, disable JFR for backward compatibility
      Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
      Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
    }

    // Apply emit_experimental_metrics (supports both old and new names)
    boolean emitExperimentalTelemetry =
        config.getBoolean("emit_experimental_telemetry/development", false);
    if (emitExperimentalTelemetry) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry is deprecated and"
              + " will be removed in 3.0. Use"
              + " otel.instrumentation.runtime-telemetry.emit-experimental-metrics instead.");
    }
    if (emitExperimentalMetrics || emitExperimentalTelemetry) {
      Experimental.setEmitExperimentalMetrics(builder, true);
    }

    jfrMetricSelection.apply(builder, emptyList(), false);

    // Apply capture_gc_cause. GC cause is always captured when emitting stable JVM semantic
    // conventions and is no longer configurable; otherwise it defaults to false.
    Boolean captureGcCauseConfig = config.getBoolean("capture_gc_cause");
    if (captureGcCauseConfig != null) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.capture-gc-cause is deprecated and will be removed in 3.0. GC cause will always be captured.");
    }
    boolean captureGcCause =
        SemconvStability.v3Preview() || Boolean.TRUE.equals(captureGcCauseConfig);
    Internal.setCaptureGcCause(builder, captureGcCause);
  }

  private static final class JfrMetricSelection {
    private final boolean selectorPresent;
    private final List<String> included;
    private final List<String> excluded;
    private final boolean emitExperimentalJfrMetrics;
    private final boolean preferJfrMetrics;

    private static JfrMetricSelection create(DeclarativeConfigProperties config) {
      DeclarativeConfigProperties jfrMetrics = config.get("jfr_metrics/development");
      List<String> includedConfig = jfrMetrics.getScalarList("included", String.class);
      List<String> excludedConfig = jfrMetrics.getScalarList("excluded", String.class);
      List<String> included = includedConfig == null ? emptyList() : includedConfig;
      List<String> excluded = excludedConfig == null ? emptyList() : excludedConfig;
      return new JfrMetricSelection(
          // an empty selector is equivalent to no selector at all, matching flat configuration
          // where empty property values cannot be distinguished from unset ones
          !included.isEmpty() || !excluded.isEmpty(),
          included,
          excluded,
          config.getBoolean("emit_experimental_jfr_metrics/development", false),
          config.getBoolean("prefer_jfr/development", false));
    }

    private JfrMetricSelection(
        boolean selectorPresent,
        List<String> included,
        List<String> excluded,
        boolean emitExperimentalJfrMetrics,
        boolean preferJfrMetrics) {
      this.selectorPresent = selectorPresent;
      this.included = included;
      this.excluded = excluded;
      this.emitExperimentalJfrMetrics = emitExperimentalJfrMetrics;
      this.preferJfrMetrics = preferJfrMetrics;
    }

    private boolean isConfigured() {
      return selectorPresent || emitExperimentalJfrMetrics || preferJfrMetrics;
    }

    private void apply(
        RuntimeTelemetryBuilder builder,
        List<String> compatibilityIncluded,
        boolean compatibilitySelectsAll) {
      if (emitExperimentalJfrMetrics) {
        Experimental.setEmitExperimentalJfrMetrics(builder, true);
      }
      if (preferJfrMetrics) {
        logger.warning(
            "otel.instrumentation.runtime-telemetry.experimental.prefer-jfr is deprecated and may be"
                + " removed in the next minor release. Use"
                + " otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included instead.");
      }

      if (!selectorPresent
          && compatibilityIncluded.isEmpty()
          && !compatibilitySelectsAll
          && !preferJfrMetrics) {
        return;
      }

      boolean selectsAll = compatibilitySelectsAll || (selectorPresent && included.isEmpty());
      List<String> effectiveIncluded = new ArrayList<>();
      if (!selectsAll) {
        effectiveIncluded.addAll(included);
        effectiveIncluded.addAll(compatibilityIncluded);
        if (preferJfrMetrics) {
          effectiveIncluded.addAll(Experimental.JMX_OVERLAPPING_JFR_METRICS);
        }
      }
      Internal.setJfrMetrics(
          builder,
          IncludeExclude.builder().setIncluded(effectiveIncluded).setExcluded(excluded).build());
    }
  }

  private Internal() {}
}
