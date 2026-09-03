/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import javax.annotation.Nullable;

/**
 * The {@link MessagingTelemetrySignals} held by a {@link Context}, used to coordinate messaging
 * telemetry between an operation and the messaging operations nested inside it.
 *
 * <p>Tracking is opt-in. Signals are only remembered after an instrumentation calls {@link
 * #enable(Context)} on the context it starts its nested work with. Two unrelated messaging
 * instrumentations that happen to nest therefore continue emitting telemetry independently. Signal
 * presence can mean the signal was already emitted or that an outer operation selected it for
 * emission.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingTelemetryState {

  private static final ContextKey<MessagingTelemetrySignals> MESSAGING_TELEMETRY_SIGNALS =
      ContextKey.named("messaging-telemetry-signals");

  /**
   * Returns a context that remembers messaging telemetry signals for operations nested inside it.
   */
  public static Context enable(Context context) {
    return isEnabled(context)
        ? context
        : context.with(MESSAGING_TELEMETRY_SIGNALS, signals(context));
  }

  public static boolean isEnabled(Context context) {
    return context.get(MESSAGING_TELEMETRY_SIGNALS) != null;
  }

  public static boolean contains(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    return operation != null && signals(context).contains(operation, signal);
  }

  /** Adds a signal, whether or not this context remembers signals yet. */
  public static Context add(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    if (operation == null) {
      return context;
    }
    return context.with(MESSAGING_TELEMETRY_SIGNALS, signals(context).with(operation, signal));
  }

  /**
   * Adds a signal only when this context remembers signals, so an instrumentation that never opted
   * in does not affect nested operations.
   */
  public static Context addIfEnabled(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    return isEnabled(context) ? add(context, operation, signal) : context;
  }

  private static MessagingTelemetrySignals signals(Context context) {
    MessagingTelemetrySignals signals = context.get(MESSAGING_TELEMETRY_SIGNALS);
    return signals == null ? MessagingTelemetrySignals.none() : signals;
  }

  private MessagingTelemetryState() {}
}
