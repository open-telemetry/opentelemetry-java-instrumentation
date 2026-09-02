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
 * An immutable set of claims on messaging telemetry, where a claim is one {@link
 * MessagingTelemetrySignal} of one {@link MessagingOperationType}.
 *
 * <p>A claim means "this layer emits this signal for this operation", so a layer nested inside it
 * has to leave that signal alone. Because operations and signals are tracked as separate claims, a
 * layer that owns the {@code send} duration still leaves the {@code receive} duration and the
 * process span to whoever owns those.
 *
 * <p>Claims are held as one bit per signal of each operation that has any, which is small enough to
 * keep in a {@link io.opentelemetry.context.Context}, in a weak map keyed by a message, or in a
 * thread local.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingTelemetryClaims {

  private static final MessagingTelemetryClaims NONE =
      new MessagingTelemetryClaims(new EnumMap<>(MessagingOperationType.class));

  private final EnumMap<MessagingOperationType, Integer> signalsByOperation;

  private MessagingTelemetryClaims(EnumMap<MessagingOperationType, Integer> signalsByOperation) {
    this.signalsByOperation = signalsByOperation;
  }

  /** Returns the empty claim set. */
  public static MessagingTelemetryClaims none() {
    return NONE;
  }

  /** Returns a claim set holding only the given signal of the given operation. */
  public static MessagingTelemetryClaims of(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return NONE.with(operation, signal);
  }

  public boolean isEmpty() {
    return signalsByOperation.isEmpty();
  }

  public boolean contains(MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return (signalsOf(operation) & signal.bit()) != 0;
  }

  public MessagingTelemetryClaims with(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return withSignals(operation, signalsOf(operation) | signal.bit());
  }

  public MessagingTelemetryClaims without(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return withSignals(operation, signalsOf(operation) & ~signal.bit());
  }

  public MessagingTelemetryClaims union(MessagingTelemetryClaims other) {
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
    return merged.equals(signalsByOperation) ? this : new MessagingTelemetryClaims(merged);
  }

  private int signalsOf(MessagingOperationType operation) {
    Integer signals = signalsByOperation.get(operation);
    return signals == null ? 0 : signals;
  }

  private MessagingTelemetryClaims withSignals(MessagingOperationType operation, int signals) {
    if (signals == signalsOf(operation)) {
      return this;
    }
    EnumMap<MessagingOperationType, Integer> updated = new EnumMap<>(signalsByOperation);
    if (signals == 0) {
      updated.remove(operation);
    } else {
      updated.put(operation, signals);
    }
    return updated.isEmpty() ? NONE : new MessagingTelemetryClaims(updated);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return obj instanceof MessagingTelemetryClaims
        && signalsByOperation.equals(((MessagingTelemetryClaims) obj).signalsByOperation);
  }

  @Override
  public int hashCode() {
    return signalsByOperation.hashCode();
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(", ", "MessagingTelemetryClaims[", "]");
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
