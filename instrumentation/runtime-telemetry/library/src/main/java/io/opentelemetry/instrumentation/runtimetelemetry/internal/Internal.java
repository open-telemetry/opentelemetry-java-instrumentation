/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
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
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setEnableAllJfrFeatures;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setDisableAllJfrFeatures;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setEnableExperimentalJfrFeatures;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setCaptureGcCause;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setUseLegacyJfrCpuCountMetric;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setJmxInstrumentationName;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setJfrInstrumentationName;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setEnableJfrFeature;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, String> setDisableJfrFeature;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setDisableJmx;

  private Internal() {}

  /**
   * Sets whether all JFR features should be enabled. This is used for backward compatibility with
   * the {@code runtime_telemetry_java17.enable_all} configuration option.
   *
   * <p>On Java 17+, this enables all JFR features including those that overlap with JMX metrics.
   */
  public static void setEnableAllJfrFeatures(
      RuntimeTelemetryBuilder builder, boolean enableAllJfr) {
    if (setEnableAllJfrFeatures != null) {
      setEnableAllJfrFeatures.accept(builder, enableAllJfr);
    }
  }

  public static void internalSetEnableAllJfrFeatures(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setEnableAllJfrFeatures = callback;
  }

  /**
   * Sets whether all JFR features should be disabled. This is used for backward compatibility when
   * running on Java 17+ but only the base {@code runtime_telemetry.enabled} option is set (without
   * {@code runtime_telemetry_java17.enabled}).
   */
  public static void setDisableAllJfrFeatures(
      RuntimeTelemetryBuilder builder, boolean disableAllJfr) {
    if (setDisableAllJfrFeatures != null) {
      setDisableAllJfrFeatures.accept(builder, disableAllJfr);
    }
  }

  public static void internalSetDisableAllJfrFeatures(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setDisableAllJfrFeatures = callback;
  }

  /**
   * Sets whether experimental JFR features should be enabled. This is used for backward
   * compatibility with the {@code runtime_telemetry_java17.enabled} configuration option, which
   * enabled experimental JFR features (context switches, locks, allocations, network I/O) but not
   * experimental JMX features.
   */
  public static void setEnableExperimentalJfrFeatures(
      RuntimeTelemetryBuilder builder, boolean enable) {
    if (setEnableExperimentalJfrFeatures != null) {
      setEnableExperimentalJfrFeatures.accept(builder, enable);
    }
  }

  public static void internalSetEnableExperimentalJfrFeatures(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setEnableExperimentalJfrFeatures = callback;
  }

  /**
   * Sets whether the GC cause attribute should be captured on GC duration metrics. The default is
   * {@code true}. This is configurable for backward compatibility with the previous behavior where
   * {@code capture_gc_cause} defaulted to {@code false}.
   *
   * @param builder the runtime telemetry builder
   * @param captureGcCause {@code true} to capture the GC cause attribute (default)
   */
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
   * Enables a specific JFR feature by name. This is used for backward compatibility with the
   * runtime-telemetry-java17 module's per-feature control.
   *
   * @param builder the runtime telemetry builder
   * @param featureName the JFR feature name (e.g., "CPU_COUNT_METRICS")
   */
  public static void setEnableJfrFeature(RuntimeTelemetryBuilder builder, String featureName) {
    if (setEnableJfrFeature != null) {
      setEnableJfrFeature.accept(builder, featureName);
    }
  }

  public static void internalSetEnableJfrFeature(
      BiConsumer<RuntimeTelemetryBuilder, String> callback) {
    Internal.setEnableJfrFeature = callback;
  }

  /**
   * Disables a specific JFR feature by name. This is used for backward compatibility with the
   * runtime-telemetry-java17 module's per-feature control.
   *
   * @param builder the runtime telemetry builder
   * @param featureName the JFR feature name (e.g., "CPU_COUNT_METRICS")
   */
  public static void setDisableJfrFeature(RuntimeTelemetryBuilder builder, String featureName) {
    if (setDisableJfrFeature != null) {
      setDisableJfrFeature.accept(builder, featureName);
    }
  }

  public static void internalSetDisableJfrFeature(
      BiConsumer<RuntimeTelemetryBuilder, String> callback) {
    Internal.setDisableJfrFeature = callback;
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
  public static RuntimeTelemetry configure(
      OpenTelemetry openTelemetry, boolean defaultEnabled) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");
    DeclarativeConfigProperties java17Config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry_java17");

    // Determine which configuration is being used
    boolean baseEnabled = config.getBoolean("enabled", defaultEnabled);
    boolean java17Enabled = java17Config.getBoolean("enabled", false);
    boolean java17EnableAll = java17Config.getBoolean("enable_all", false);

    if (!baseEnabled && !java17Enabled && !java17EnableAll) {
      return null; // Nothing is enabled
    }

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(openTelemetry);

    // Old deprecated java17 configs: FREEZE their behavior, don't apply new unified options
    if (java17EnableAll) {
      configureJava17EnableAll(builder, config);
      return builder.build();
    }
    if (java17Enabled) {
      configureJava17Enabled(builder);
      return builder.build();
    }

    // New unified config: handles both old java8 settings and new unified options
    if (baseEnabled) {
      configureUnified(builder, config);
      return builder.build();
    }

    throw new AssertionError("Unreachable: at least one enabled flag must be true");
  }

  private static void configureJava17EnableAll(
      RuntimeTelemetryBuilder builder, DeclarativeConfigProperties config) {
    logger.warning(
        "otel.instrumentation.runtime-telemetry-java17.enable-all is deprecated and will be"
            + " removed in 3.0. Use otel.instrumentation.runtime-telemetry.emit-experimental-metrics"
            + " and otel.instrumentation.runtime-telemetry.experimental.prefer-jfr instead.");
    // For backward compatibility: route JMX metrics to java8 scope, JFR metrics to java17 scope
    Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
    Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java17");
    Internal.setEnableAllJfrFeatures(builder, true);
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

  private static void configureJava17Enabled(RuntimeTelemetryBuilder builder) {
    logger.warning(
        "otel.instrumentation.runtime-telemetry-java17.enabled is deprecated and will be"
            + " removed in 3.0. Use otel.instrumentation.runtime-telemetry.emit-experimental-metrics"
            + " for experimental JFR features.");
    // Enable default JFR features: context switches, CPU count, locks, allocations, network I/O
    Internal.setEnableJfrFeature(builder, "CONTEXT_SWITCH_METRICS");
    Internal.setEnableJfrFeature(builder, "CPU_COUNT_METRICS");
    Internal.setEnableJfrFeature(builder, "LOCK_METRICS");
    Internal.setEnableJfrFeature(builder, "MEMORY_ALLOCATION_METRICS");
    Internal.setEnableJfrFeature(builder, "NETWORK_IO_METRICS");
    Internal.setUseLegacyJfrCpuCountMetric(builder, true);
    // For backward compatibility: OLD java17 module used java8's JMX factory, so JMX -> java8 scope
    Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java8");
    Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry-java17");
  }

  private static void configureUnified(
      RuntimeTelemetryBuilder builder, DeclarativeConfigProperties config) {
    // Check if user is using new unified config options
    boolean emitExperimentalMetrics =
        config.getBoolean("emit_experimental_metrics/development", false);
    boolean preferJfr = config.getBoolean("prefer_jfr/development", false);
    boolean newConfig = emitExperimentalMetrics || preferJfr;

    if (newConfig) {
      // New unified config: Use new instrumentation name for both JMX and JFR
      Internal.setJmxInstrumentationName(builder, "io.opentelemetry.runtime-telemetry");
      Internal.setJfrInstrumentationName(builder, "io.opentelemetry.runtime-telemetry");
    } else {
      // Old java8 config: JMX-only, disable JFR for backward compatibility
      Internal.setDisableAllJfrFeatures(builder, true);
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

    // Apply prefer_jfr
    if (preferJfr) {
      Experimental.setPreferJfrMetrics(builder, true);
    }

    // Apply capture_gc_cause
    boolean captureGcCause = config.getBoolean("capture_gc_cause", false);
    if (captureGcCause) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.capture-gc-cause is deprecated and will be"
              + " removed in 3.0. GC cause will always be captured.");
    }
    Internal.setCaptureGcCause(builder, captureGcCause);
  }
}
