/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.Meter;
import java.io.Closeable;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.EventSettings;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingStream;

/**
 * Configuration holder for JFR telemetry. On Java 17+, this implementation manages JFR features and
 * creates JFR telemetry.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JfrConfig {

  // Visible for testing
  public final EnumMap<JfrFeature, Boolean> enabledFeatureMap;

  private boolean useLegacyCpuCountMetric = false;

  /** Create a new JfrConfig with default feature settings. */
  public static JfrConfig create() {
    return new JfrConfig();
  }

  protected JfrConfig() {
    enabledFeatureMap = new EnumMap<>(JfrFeature.class);
    // By default, enable JFR features that don't overlap with JMX and are not experimental
    for (JfrFeature feature : JfrFeature.values()) {
      enabledFeatureMap.put(feature, !feature.overlapsWithJmx() && !feature.isExperimental());
    }
  }

  /** Enable all JFR features. */
  @CanIgnoreReturnValue
  public JfrConfig enableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(feature -> enabledFeatureMap.put(feature, true));
    return this;
  }

  /** Disable all JFR features. */
  @CanIgnoreReturnValue
  public JfrConfig disableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(feature -> enabledFeatureMap.put(feature, false));
    return this;
  }

  /** Enable experimental JFR features. */
  @CanIgnoreReturnValue
  public JfrConfig enableExperimentalFeatures() {
    Arrays.stream(JfrFeature.values())
        .filter(JfrFeature::isExperimental)
        .forEach(feature -> enabledFeatureMap.put(feature, true));
    return this;
  }

  /** Enable a specific JFR feature. */
  @CanIgnoreReturnValue
  public JfrConfig enableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, true);
    return this;
  }

  /** Enable a specific JFR feature by name. */
  @CanIgnoreReturnValue
  public JfrConfig enableFeature(String featureName) {
    enableFeature(JfrFeature.valueOf(featureName));
    return this;
  }

  /** Disable a specific JFR feature. */
  @CanIgnoreReturnValue
  public JfrConfig disableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, false);
    return this;
  }

  /** Disable a specific JFR feature by name. */
  @CanIgnoreReturnValue
  public JfrConfig disableFeature(String featureName) {
    disableFeature(JfrFeature.valueOf(featureName));
    return this;
  }

  /**
   * Sets whether to use the legacy metric name {@code jvm.cpu.limit} instead of the standard {@code
   * jvm.cpu.count}. This is for backward compatibility with previous versions.
   */
  @CanIgnoreReturnValue
  public JfrConfig setUseLegacyJfrCpuCountMetric(boolean useLegacy) {
    this.useLegacyCpuCountMetric = useLegacy;
    return this;
  }

  /**
   * Build JFR telemetry based on the current configuration.
   *
   * @param preferJfrMetrics if true, enable JFR features that overlap with JMX
   * @param meter the Meter to use for metrics
   * @return the JFR telemetry closeable, or null if JFR is not available or all features are
   *     disabled
   */
  @Nullable
  public AutoCloseable buildJfrTelemetry(boolean preferJfrMetrics, Meter meter) {
    // If preferJfrMetrics is set, enable JFR features that overlap with JMX
    if (preferJfrMetrics) {
      for (JfrFeature feature : JfrFeature.values()) {
        if (feature.overlapsWithJmx()) {
          enabledFeatureMap.put(feature, true);
        }
      }
    }

    if (enabledFeatureMap.values().stream().noneMatch(isEnabled -> isEnabled)) {
      return null;
    }
    return JfrRuntimeMetrics.build(meter, enabledFeatureMap::get, useLegacyCpuCountMetric);
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class JfrRuntimeMetrics implements Closeable {
    private final List<RecordedEventHandler> recordedEventHandlers;
    private final RecordingStream recordingStream;
    private final CountDownLatch startUpLatch = new CountDownLatch(1);
    private volatile boolean closed = false;

    private JfrRuntimeMetrics(
        Meter meter, Predicate<JfrFeature> featurePredicate, boolean useLegacyCpuCountMetric) {
      this.recordedEventHandlers =
          HandlerRegistry.getHandlers(meter, featurePredicate, useLegacyCpuCountMetric);
      recordingStream = new RecordingStream();
      recordedEventHandlers.forEach(
          handler -> {
            EventSettings eventSettings = recordingStream.enable(handler.getEventName());
            handler.getPollingDuration().ifPresent(eventSettings::withPeriod);
            handler.getThreshold().ifPresent(eventSettings::withThreshold);
            recordingStream.onEvent(handler.getEventName(), handler);
          });
      recordingStream.onMetadata(event -> startUpLatch.countDown());
      Thread daemonRunner =
          new Thread(this::startRecordingStream, "OpenTelemetry JFR-Metrics-Runner");
      daemonRunner.setDaemon(true);
      daemonRunner.setContextClassLoader(null);
      daemonRunner.start();
    }

    private void startRecordingStream() {
      if (closed) {
        return;
      }

      try {
        recordingStream.start();
      } catch (IllegalStateException exception) {
        // Can happen when close is called at the same time as start
        if (!closed) {
          throw exception;
        }
      }
    }

    @Nullable
    static JfrRuntimeMetrics build(
        Meter meter, Predicate<JfrFeature> featurePredicate, boolean useLegacyCpuCountMetric) {
      if (!isJfrAvailable()) {
        return null;
      }
      return new JfrRuntimeMetrics(meter, featurePredicate, useLegacyCpuCountMetric);
    }

    @Override
    public void close() {
      closed = true;
      recordingStream.close();
      recordedEventHandlers.forEach(RecordedEventHandler::close);
    }

    // Visible for testing
    public List<RecordedEventHandler> getRecordedEventHandlers() {
      return recordedEventHandlers;
    }

    // Visible for testing
    public RecordingStream getRecordingStream() {
      return recordingStream;
    }

    // Visible for testing
    public CountDownLatch getStartUpLatch() {
      return startUpLatch;
    }

    private static boolean isJfrAvailable() {
      try {
        return FlightRecorder.isAvailable();
      } catch (Throwable e) {
        // NoClassDefFoundError, UnsatisfiedLinkError (native images), or other issues
        return false;
      }
    }
  }
}
