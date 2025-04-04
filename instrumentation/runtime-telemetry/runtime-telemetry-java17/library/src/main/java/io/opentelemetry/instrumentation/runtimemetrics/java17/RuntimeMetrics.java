/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jdk.jfr.EventSettings;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingStream;

/** The entry point class for runtime metrics support using JFR and JMX. */
public final class RuntimeMetrics implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(RuntimeMetrics.class.getName());

  private final AtomicBoolean isClosed = new AtomicBoolean();
  private final MeterProvider meterProvider;
  private final List<AutoCloseable> observables;

  @Nullable private final JfrRuntimeMetrics jfrRuntimeMetrics;

  RuntimeMetrics(
      MeterProvider meterProvider,
      List<AutoCloseable> observables,
      @Nullable JfrRuntimeMetrics jfrRuntimeMetrics) {
    this.meterProvider = meterProvider;
    this.observables = List.copyOf(observables);
    this.jfrRuntimeMetrics = jfrRuntimeMetrics;
  }

  /**
   * Create and start {@link RuntimeMetrics}, configured with the default {@link JfrFeature}s.
   *
   * <p>Listens for select JFR events, extracts data, and records to various metrics. Recording will
   * continue until {@link #close()} is called.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeMetrics create(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry.getMeterProvider()).build();
  }

  /**
   * Create and start {@link RuntimeMetrics}, configured with the default {@link JfrFeature}s.
   *
   * <p>Listens for select JFR events, extracts data, and records to various metrics. Recording will
   * continue until {@link #close()} is called.
   *
   * @param meterProvider the {@link MeterProvider} instance used to record telemetry
   */
  public static RuntimeMetrics create(MeterProvider meterProvider) {
    return new RuntimeMetricsBuilder(meterProvider).build();
  }

  /**
   * Create a builder for configuring {@link RuntimeMetrics}.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeMetricsBuilder builder(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry.getMeterProvider());
  }

  /**
   * Create a builder for configuring {@link RuntimeMetrics}.
   *
   * @param meterProvider the {@link MeterProvider} instance used to record telemetry
   */
  public static RuntimeMetricsBuilder builder(MeterProvider meterProvider) {
    return new RuntimeMetricsBuilder(meterProvider);
  }

  // Visible for testing
  MeterProvider getMeterProvider() {
    return meterProvider;
  }

  // Visible for testing
  JfrRuntimeMetrics getJfrRuntimeMetrics() {
    return jfrRuntimeMetrics;
  }

  /** Stop recording JFR events. */
  @Override
  public void close() {
    if (!isClosed.compareAndSet(false, true)) {
      logger.log(Level.WARNING, "RuntimeMetrics is already closed");
      return;
    }
    if (jfrRuntimeMetrics != null) {
      jfrRuntimeMetrics.close();
    }

    JmxRuntimeMetricsUtil.closeObservers(observables);
  }

  static class JfrRuntimeMetrics implements Closeable {
    private final List<RecordedEventHandler> recordedEventHandlers;
    private final RecordingStream recordingStream;
    private final CountDownLatch startUpLatch = new CountDownLatch(1);

    private JfrRuntimeMetrics(MeterProvider meterProvider, Predicate<JfrFeature> featurePredicate) {
      this.recordedEventHandlers = HandlerRegistry.getHandlers(meterProvider, featurePredicate);
      recordingStream = new RecordingStream();
      recordedEventHandlers.forEach(
          handler -> {
            EventSettings eventSettings = recordingStream.enable(handler.getEventName());
            handler.getPollingDuration().ifPresent(eventSettings::withPeriod);
            handler.getThreshold().ifPresent(eventSettings::withThreshold);
            recordingStream.onEvent(handler.getEventName(), handler);
          });
      recordingStream.onMetadata(event -> startUpLatch.countDown());
      Thread daemonRunner = new Thread(recordingStream::start, "OpenTelemetry JFR-Metrics-Runner");
      daemonRunner.setDaemon(true);
      daemonRunner.start();
    }

    static JfrRuntimeMetrics build(
        MeterProvider meterProvider, Predicate<JfrFeature> featurePredicate) {
      if (!isJfrAvailable()) {
        return null;
      }
      return new JfrRuntimeMetrics(meterProvider, featurePredicate);
    }

    @Override
    public void close() {
      recordingStream.close();
      recordedEventHandlers.forEach(RecordedEventHandler::close);
    }

    // Visible for testing
    List<RecordedEventHandler> getRecordedEventHandlers() {
      return recordedEventHandlers;
    }

    // Visible for testing
    RecordingStream getRecordingStream() {
      return recordingStream;
    }

    // Visible for testing
    CountDownLatch getStartUpLatch() {
      return startUpLatch;
    }

    private static boolean isJfrAvailable() {
      try {
        Class.forName("jdk.jfr.FlightRecorder");
        // UnsatisfiedLinkError or ClassNotFoundException
      } catch (Exception e) {
        return false;
      }

      return FlightRecorder.isAvailable();
    }
  }
}
