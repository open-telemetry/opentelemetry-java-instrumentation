/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.RecordedEventHandler;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.jfr.EventSettings;
import jdk.jfr.consumer.RecordingStream;

/** The entry point class for the JFR-over-OpenTelemetry support. */
public final class JfrTelemetry implements Closeable {

  private static final Logger logger = Logger.getLogger(JfrTelemetry.class.getName());

  private final AtomicBoolean isClosed = new AtomicBoolean();
  private final OpenTelemetry openTelemetry;
  private final List<RecordedEventHandler> recordedEventHandlers;
  private final RecordingStream recordingStream;

  @SuppressWarnings("CatchingUnchecked")
  JfrTelemetry(OpenTelemetry openTelemetry, Predicate<JfrFeature> featurePredicate) {
    this.openTelemetry = openTelemetry;
    this.recordedEventHandlers = HandlerRegistry.getHandlers(openTelemetry, featurePredicate);
    try {
      recordingStream = new RecordingStream();
      recordedEventHandlers.forEach(
          handler -> {
            EventSettings eventSettings = recordingStream.enable(handler.getEventName());
            handler.getPollingDuration().ifPresent(eventSettings::withPeriod);
            handler.getThreshold().ifPresent(eventSettings::withThreshold);
            recordingStream.onEvent(handler.getEventName(), handler);
          });
      Thread daemonRunner = new Thread(() -> recordingStream.start());
      daemonRunner.setDaemon(true);
      daemonRunner.start();
    } catch (Exception e) {
      close();
      throw new IllegalStateException("Error starting JfrTelemetry", e);
    }
  }

  /**
   * Create and start {@link JfrTelemetry}, configured with the default {@link JfrFeature}s.
   *
   * <p>Listens for select JFR events, extracts data, and records to various metrics. Recording will
   * continue until {@link #close()} is called.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static JfrTelemetry create(OpenTelemetry openTelemetry) {
    return new JfrTelemetryBuilder(openTelemetry).build();
  }

  /**
   * Create a builder for configuring {@link JfrTelemetry}.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static JfrTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JfrTelemetryBuilder(openTelemetry);
  }

  // Visible for testing
  OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  // Visible for testing
  List<RecordedEventHandler> getRecordedEventHandlers() {
    return recordedEventHandlers;
  }

  // Visible for testing
  RecordingStream getRecordingStream() {
    return recordingStream;
  }

  /** Stop recording JFR events. */
  @Override
  public void close() {
    if (!isClosed.compareAndSet(false, true)) {
      logger.log(Level.WARNING, "JfrTelemetry is already closed");
      return;
    }
    recordingStream.close();
    recordedEventHandlers.forEach(RecordedEventHandler::close);
  }
}
