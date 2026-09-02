/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;

/**
 * The {@link MessagingTelemetryClaims} an instrumentation has taken for the current thread, so that
 * another instrumentation running underneath it leaves those signals alone.
 *
 * <p>This is what a layer reaches for when it has to reach an inner instrumentation it cannot name.
 * Spring Rabbit, for example, knows its listener container will create the process span, but the
 * object that would otherwise create one is a RabbitMQ consumer it never sees; claiming the process
 * span for the duration of the registration call tells RabbitMQ to stay out of the way.
 *
 * <h2>One instance per coordinating stack</h2>
 *
 * <p>Claims are held per instance, not globally. A stack that coordinates with the instrumentations
 * below it keeps its own instance in a bootstrap module, which makes that holder's class identity
 * the coordination key: opaque, unforgeable, and the same object for every class loader, without
 * names or a registry to look owners up in.
 *
 * <p>Keeping them per instance is what makes the claims safe. Threads are shared by whatever
 * happens to run on them, so a Kafka poll loop that claims the process span must not silence an
 * unrelated RabbitMQ delivery that lands on the same thread. Claims taken through one instance are
 * invisible to every other instance, so only the stack that made them is affected.
 *
 * <h2>Restoring</h2>
 *
 * <p>Claims nest. Keep what {@link #suppress} returns and hand it back to {@link #restore} when the
 * call that made the claim returns, including when it throws, so the thread ends up exactly where
 * it started:
 *
 * <pre>{@code
 * MessagingTelemetryClaims previous = suppression.suppress(PROCESS, SPAN);
 * try {
 *   ...
 * } finally {
 *   suppression.restore(previous);
 * }
 * }</pre>
 */
public final class MessagingTelemetrySuppression {

  // one thread local per coordinating stack is the point: a stack holds a single instance in a
  // static field of its bootstrap holder, and keeping the claims here rather than in a static field
  // of this class is what stops one stack from seeing another one's claims
  @SuppressWarnings("ThreadLocalUsage")
  private final ThreadLocal<MessagingTelemetryClaims> claims = new ThreadLocal<>();

  /** Returns a new set of claims, for one coordinating instrumentation stack to hold. */
  public static MessagingTelemetrySuppression create() {
    return new MessagingTelemetrySuppression();
  }

  /** Returns what this stack has claimed for the current thread. */
  public MessagingTelemetryClaims current() {
    MessagingTelemetryClaims current = claims.get();
    return current == null ? MessagingTelemetryClaims.none() : current;
  }

  /**
   * Claims a signal for the current thread and returns what was claimed before, to hand back to
   * {@link #restore} once the claim no longer applies.
   */
  public MessagingTelemetryClaims suppress(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    MessagingTelemetryClaims previous = current();
    restore(previous.with(operation, signal));
    return previous;
  }

  /** Makes {@code previous} what this stack claims for the current thread. */
  public void restore(MessagingTelemetryClaims previous) {
    if (previous.isEmpty()) {
      claims.remove();
    } else {
      claims.set(previous);
    }
  }

  public boolean isSuppressed(MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return current().contains(operation, signal);
  }

  private MessagingTelemetrySuppression() {}
}
