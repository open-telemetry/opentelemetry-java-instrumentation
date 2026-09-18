/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * An immutable set of messaging telemetry signals, where each entry is one {@link
 * MessagingTelemetrySignal} of one {@link MessagingOperationType}.
 *
 * <p>Signal presence means either that the signal was already emitted or that an outer operation
 * selected it for emission. A nested operation can use that information to avoid emitting the same
 * signal.
 *
 * <p>Signals are held as one bit per signal of each operation that has any. The representation is
 * small enough to keep in a {@link io.opentelemetry.context.Context}, in a weak map keyed by a
 * message, or in a thread local.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingTelemetrySignals {

  private static final MessagingTelemetrySignals NONE =
      new MessagingTelemetrySignals(new EnumMap<>(MessagingOperationType.class));

  private final EnumMap<MessagingOperationType, Integer> signalsByOperation;

  private MessagingTelemetrySignals(EnumMap<MessagingOperationType, Integer> signalsByOperation) {
    this.signalsByOperation = signalsByOperation;
  }

  /** Returns the empty signal set. */
  public static MessagingTelemetrySignals none() {
    return NONE;
  }

  /** Returns a set holding only the given signal of the given operation. */
  public static MessagingTelemetrySignals of(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return NONE.with(operation, signal);
  }

  public boolean isEmpty() {
    return signalsByOperation.isEmpty();
  }

  public boolean contains(MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return (signalsOf(operation) & signal.bit()) != 0;
  }

  public MessagingTelemetrySignals with(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return withSignals(operation, signalsOf(operation) | signal.bit());
  }

  public MessagingTelemetrySignals without(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return withSignals(operation, signalsOf(operation) & ~signal.bit());
  }

  public MessagingTelemetrySignals union(MessagingTelemetrySignals other) {
    if (other.isEmpty()) {
      return this;
    }
    if (isEmpty()) {
      return other;
    }
    EnumMap<MessagingOperationType, Integer> merged = new EnumMap<>(signalsByOperation);
    for (Map.Entry<MessagingOperationType, Integer> entry : other.signalsByOperation.entrySet()) {
      merged.merge(
          entry.getKey(), entry.getValue(), (signals, otherSignals) -> signals | otherSignals);
    }
    return merged.equals(signalsByOperation) ? this : new MessagingTelemetrySignals(merged);
  }

  private int signalsOf(MessagingOperationType operation) {
    Integer signals = signalsByOperation.get(operation);
    return signals == null ? 0 : signals;
  }

  private MessagingTelemetrySignals withSignals(MessagingOperationType operation, int signals) {
    if (signals == signalsOf(operation)) {
      return this;
    }
    EnumMap<MessagingOperationType, Integer> updated = new EnumMap<>(signalsByOperation);
    if (signals == 0) {
      updated.remove(operation);
    } else {
      updated.put(operation, signals);
    }
    return updated.isEmpty() ? NONE : new MessagingTelemetrySignals(updated);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return obj instanceof MessagingTelemetrySignals
        && signalsByOperation.equals(((MessagingTelemetrySignals) obj).signalsByOperation);
  }

  @Override
  public int hashCode() {
    return signalsByOperation.hashCode();
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(", ", "MessagingTelemetrySignals[", "]");
    for (Map.Entry<MessagingOperationType, Integer> entry : signalsByOperation.entrySet()) {
      for (MessagingTelemetrySignal signal : MessagingTelemetrySignal.values()) {
        if ((entry.getValue() & signal.bit()) != 0) {
          joiner.add(entry.getKey() + "." + signal);
        }
      }
    }
    return joiner.toString();
  }
}
