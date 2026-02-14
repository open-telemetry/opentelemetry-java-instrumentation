/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** The entry point class for runtime telemetry support using JMX (Java 8+) and JFR (Java 17+). */
public final class RuntimeTelemetry implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(RuntimeTelemetry.class.getName());

  private final AtomicBoolean isClosed = new AtomicBoolean();
  private final List<AutoCloseable> observables;
  @Nullable private final AutoCloseable jfrTelemetry;

  RuntimeTelemetry(List<AutoCloseable> observables, @Nullable AutoCloseable jfrTelemetry) {
    this.observables = Collections.unmodifiableList(observables);
    this.jfrTelemetry = jfrTelemetry;
  }

  /**
   * Create and start {@link RuntimeTelemetry}.
   *
   * <p>Listens for select JMX beans (and JFR events on Java 17+), extracts data, and records to
   * various metrics. Recording will continue until {@link #close()} is called.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeTelemetry create(OpenTelemetry openTelemetry) {
    return new RuntimeTelemetryBuilder(openTelemetry).build();
  }

  /**
   * Create a builder for configuring {@link RuntimeTelemetry}.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RuntimeTelemetryBuilder(openTelemetry);
  }

  // Only used by tests
  @Nullable
  AutoCloseable getJfrTelemetry() {
    return jfrTelemetry;
  }

  /** Stop recording metrics. */
  @Override
  public void close() {
    if (!isClosed.compareAndSet(false, true)) {
      logger.log(Level.WARNING, "RuntimeTelemetry is already closed");
      return;
    }

    if (jfrTelemetry != null) {
      try {
        jfrTelemetry.close();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error closing JFR telemetry", e);
      }
    }

    for (AutoCloseable observable : observables) {
      try {
        observable.close();
      } catch (Exception e) {
        // Ignore
      }
    }
  }
}
