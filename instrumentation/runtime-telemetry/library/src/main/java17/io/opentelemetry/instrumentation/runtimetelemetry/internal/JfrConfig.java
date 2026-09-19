/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptySet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.Meter;
import java.io.Closeable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.EventSettings;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingStream;

/**
 * Configuration holder for JFR telemetry. On Java 17+, this implementation manages JFR metrics and
 * creates JFR telemetry.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JfrConfig {

  private boolean useLegacyCpuCountMetric = false;

  public static JfrConfig create() {
    return new JfrConfig();
  }

  private JfrConfig() {}

  /**
   * Sets whether to use the legacy metric name {@code jvm.cpu.limit} instead of the standard {@code
   * jvm.cpu.count}. This is for backward compatibility with previous versions.
   */
  @CanIgnoreReturnValue
  public JfrConfig setUseLegacyJfrCpuCountMetric(boolean useLegacy) {
    this.useLegacyCpuCountMetric = useLegacy;
    return this;
  }

  public JfrTelemetry buildJfrTelemetry(
      Predicate<String> metricNamePredicate,
      Meter meter,
      boolean requireCompleteJmxReplacement,
      boolean emitExperimentalJmxMetrics) {
    JfrRuntimeMetrics telemetry =
        JfrRuntimeMetrics.build(
            meter,
            metricNamePredicate,
            useLegacyCpuCountMetric,
            requireCompleteJmxReplacement,
            emitExperimentalJmxMetrics);
    if (telemetry == null) {
      return new JfrTelemetry(null, emptySet());
    }
    return new JfrTelemetry(telemetry, telemetry.getMetricNames());
  }

  /**
   * JFR telemetry and the metric names it registered.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class JfrTelemetry {
    @Nullable private final AutoCloseable telemetry;
    private final Set<String> metricNames;

    public JfrTelemetry(@Nullable AutoCloseable telemetry, Set<String> metricNames) {
      this.telemetry = telemetry;
      this.metricNames = Collections.unmodifiableSet(new HashSet<>(metricNames));
    }

    @Nullable
    public AutoCloseable getTelemetry() {
      return telemetry;
    }

    public Set<String> getMetricNames() {
      return metricNames;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class JfrRuntimeMetrics implements Closeable {
    private final List<RecordedEventHandler> recordedEventHandlers;
    private final Set<String> metricNames;
    private final RecordingStream recordingStream;
    private final CountDownLatch startUpLatch = new CountDownLatch(1);
    private volatile boolean closed = false;

    private JfrRuntimeMetrics(
        List<RecordedEventHandler> recordedEventHandlers, Set<String> metricNames) {
      this.recordedEventHandlers = recordedEventHandlers;
      this.metricNames = Collections.unmodifiableSet(new HashSet<>(metricNames));
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
      } catch (IllegalStateException e) {
        // Can happen when close is called at the same time as start
        if (!closed) {
          throw e;
        }
      }
    }

    @Nullable
    static JfrRuntimeMetrics build(
        Meter meter,
        Predicate<String> metricNamePredicate,
        boolean useLegacyCpuCountMetric,
        boolean requireCompleteJmxReplacement,
        boolean emitExperimentalJmxMetrics) {
      if (!isJfrAvailable()) {
        return null;
      }
      List<RecordedEventHandler> handlers =
          HandlerRegistry.getHandlers(
              meter,
              metricNamePredicate,
              useLegacyCpuCountMetric,
              requireCompleteJmxReplacement,
              emitExperimentalJmxMetrics);
      if (handlers.isEmpty()) {
        return null;
      }
      Set<String> metricNames = new HashSet<>();
      handlers.stream()
          .flatMap(handler -> handler.getMetricNames().stream())
          .forEach(metricNames::add);
      return new JfrRuntimeMetrics(handlers, metricNames);
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

    public Set<String> getMetricNames() {
      return metricNames;
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
      } catch (Throwable t) {
        // NoClassDefFoundError, UnsatisfiedLinkError (native images), or other issues
        return false;
      }
    }
  }
}
