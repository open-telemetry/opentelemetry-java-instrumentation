/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;

/**
 * The {@link MessagingTelemetrySignals} suppressed by an instrumentation for the current thread.
 *
 * <p>This is what a layer reaches for when it has to reach an inner instrumentation it cannot name.
 * Spring Rabbit, for example, knows its listener container will create the process span, but the
 * object that would otherwise create one is a RabbitMQ consumer it never sees. Suppressing the
 * process span for the duration of the registration call tells RabbitMQ to stay out of the way.
 *
 * <h2>One instance per coordinating stack</h2>
 *
 * <p>Suppressed signals are held per instance, not globally. A stack that coordinates with the
 * instrumentations below it keeps a separate instance in a bootstrap module, which makes that
 * holder's class identity the coordination key: opaque, unforgeable, and the same object for every
 * class loader, without names or a registry.
 *
 * <p>Threads are shared by whatever happens to run on them, so a Kafka poll loop that suppresses
 * the process span must not silence an unrelated RabbitMQ delivery that lands on the same thread.
 * Signals suppressed through one instance are invisible to every other instance.
 *
 * <h2>Restoring</h2>
 *
 * <p>Suppression nests. Keep what {@link #suppress} returns and hand it back to {@link #restore}
 * when the call returns, including when it throws, so the thread ends up exactly where it started:
 *
 * <pre>{@code
 * MessagingTelemetrySignals previous = suppression.suppress(PROCESS, SPAN);
 * try {
 *   ...
 * } finally {
 *   suppression.restore(previous);
 * }
 * }</pre>
 */
public final class MessagingTelemetrySuppression {

  // One thread local per coordinating stack is the point. A stack holds a single instance in a
  // static field of its bootstrap holder. Keeping signals here instead of a static field on this
  // class stops one stack from seeing another's suppressed signals.
  @SuppressWarnings("ThreadLocalUsage")
  private final ThreadLocal<MessagingTelemetrySignals> signals = new ThreadLocal<>();

  /** Returns a new suppression set for one coordinating instrumentation stack to hold. */
  public static MessagingTelemetrySuppression create() {
    return new MessagingTelemetrySuppression();
  }

  /** Returns the signals this stack suppresses for the current thread. */
  public MessagingTelemetrySignals current() {
    MessagingTelemetrySignals current = signals.get();
    return current == null ? MessagingTelemetrySignals.none() : current;
  }

  /**
   * Suppresses a signal for the current thread and returns the previous suppressed signals, to hand
   * back to {@link #restore} once the suppression no longer applies.
   */
  public MessagingTelemetrySignals suppress(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    MessagingTelemetrySignals previous = current();
    restore(previous.with(operation, signal));
    return previous;
  }

  /** Restores the signals this stack suppresses for the current thread. */
  public void restore(MessagingTelemetrySignals previous) {
    if (previous.isEmpty()) {
      signals.remove();
    } else {
      signals.set(previous);
    }
  }

  public boolean isSuppressed(MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return current().contains(operation, signal);
  }

  private MessagingTelemetrySuppression() {}
}
