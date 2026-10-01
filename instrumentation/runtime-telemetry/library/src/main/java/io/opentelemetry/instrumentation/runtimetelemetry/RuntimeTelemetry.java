/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Collections.emptySet;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** The entry point class for runtime telemetry support using JMX (Java 8+) and JFR (Java 17+). */
public final class RuntimeTelemetry implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(RuntimeTelemetry.class.getName());

  private final AtomicBoolean isClosed = new AtomicBoolean();
  private final List<AutoCloseable> observables;
  private final Set<String> registeredJmxObservers;
  @Nullable private final AutoCloseable jfrTelemetry;

  static {
    // a closed instance no longer observes anything, so it reports no registered observers
    Internal.internalSetRegisteredJmxObservers(
        telemetry -> telemetry.isClosed.get() ? emptySet() : telemetry.registeredJmxObservers);
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

  RuntimeTelemetry(List<AutoCloseable> observables, @Nullable AutoCloseable jfrTelemetry) {
    this(observables, jfrTelemetry, emptySet());
  }

  RuntimeTelemetry(
      List<AutoCloseable> observables,
      @Nullable AutoCloseable jfrTelemetry,
      Set<String> registeredJmxObservers) {
    this.registeredJmxObservers = Collections.unmodifiableSet(registeredJmxObservers);
    this.observables = Collections.unmodifiableList(observables);
    this.jfrTelemetry = jfrTelemetry;
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
      logger.log(WARNING, "RuntimeTelemetry is already closed");
      return;
    }

    if (jfrTelemetry != null) {
      try {
        jfrTelemetry.close();
      } catch (Exception e) {
        logger.log(WARNING, "Error closing JFR telemetry", e);
      }
    }

    for (AutoCloseable observable : observables) {
      try {
        observable.close();
      } catch (Exception e) {
        logger.log(WARNING, "Error closing runtime telemetry observable", e);
      }
    }
  }
}
