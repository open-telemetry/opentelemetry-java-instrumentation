/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

/**
 * Accesses the {@link MessagingTelemetrySignals} attached to objects that carry in-flight messages.
 *
 * <p>Signals live here rather than in the {@link io.opentelemetry.context.Context} when the message
 * outlives the scope that emitted telemetry for it, or reaches the next instrumentation on a
 * different thread. A JMS message that already has a receive span, for instance, still has it when
 * a listener picks it up later.
 *
 * <p>Instrumentations that coordinate through a carrier must create accessors with the same virtual
 * field type pair.
 */
public final class MessagingTelemetryCarrier<T> {

  private final VirtualField<T, MessagingTelemetrySignals> signalsField;

  public static <T> MessagingTelemetryCarrier<T> create(
      VirtualField<T, MessagingTelemetrySignals> signalsField) {
    return new MessagingTelemetryCarrier<>(signalsField);
  }

  private MessagingTelemetryCarrier(VirtualField<T, MessagingTelemetrySignals> signalsField) {
    this.signalsField = signalsField;
  }

  /** Returns the signals held by {@code carrier}, or no signals when it is null or has none. */
  public MessagingTelemetrySignals getSignals(@Nullable T carrier) {
    if (carrier == null) {
      return MessagingTelemetrySignals.none();
    }
    MessagingTelemetrySignals signals = signalsField.get(carrier);
    return signals == null ? MessagingTelemetrySignals.none() : signals;
  }

  public boolean contains(
      @Nullable T carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return getSignals(carrier).contains(operation, signal);
  }

  public void add(
      @Nullable T carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    if (carrier != null) {
      synchronized (carrier) {
        signalsField.set(carrier, getSignals(carrier).with(operation, signal));
      }
    }
  }

  /**
   * Adds everything {@code source} holds to what {@code target} already holds, for an object that
   * wraps or continues another one without replacing what is known about it.
   */
  public <S> void mergeFrom(
      MessagingTelemetryCarrier<S> sourceCarrier, @Nullable S source, @Nullable T target) {
    MessagingTelemetrySignals signals = sourceCarrier.getSignals(source);
    if (target == null || signals.isEmpty()) {
      return;
    }
    synchronized (target) {
      signalsField.set(target, getSignals(target).union(signals));
    }
  }

  /**
   * Makes {@code target} hold exactly what {@code source} holds, for an object that is being reused
   * or refilled and must not keep the signals of the message it carried before.
   */
  public <S> void replaceFrom(
      MessagingTelemetryCarrier<S> sourceCarrier, @Nullable S source, @Nullable T target) {
    if (target == null) {
      return;
    }
    MessagingTelemetrySignals signals = sourceCarrier.getSignals(source);
    synchronized (target) {
      if (signals.isEmpty()) {
        signalsField.set(target, null);
      } else {
        signalsField.set(target, signals);
      }
    }
  }

  /** Forgets every signal on {@code carrier}. */
  public void clear(@Nullable T carrier) {
    if (carrier != null) {
      synchronized (carrier) {
        signalsField.set(carrier, null);
      }
    }
  }
}
