/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15.Resilience4jCircuitBreakerSingletons.instrumenter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

public class Resilience4jCircuitBreakerSpans {

  private static final ThreadLocal<Deque<PendingSpan>> pendingSpans = new ThreadLocal<>();
  // Resilience4j's raw acquirePermission/onSuccess/onError API does not expose an attempt token.
  // Keep a weak per-breaker fallback so callbacks on a different thread can still close the
  // oldest pending attempt without retaining circuit breakers indefinitely.
  private static final Map<CircuitBreaker, Deque<PendingSpan>> pendingSpansByCircuitBreaker =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<Boolean> inCircuitBreakerCallback = new ThreadLocal<>();
  private static final ThreadLocal<Deque<Boolean>> onResultEnded = new ThreadLocal<>();

  public static void start(CircuitBreaker circuitBreaker) {
    Context parentContext = Context.current();
    Resilience4jCircuitBreakerRequest request =
        Resilience4jCircuitBreakerRequest.create(circuitBreaker);
    if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      // Circuit breaker spans are internal and noisy without an existing trace.
      return;
    }
    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    PendingSpan pendingSpan =
        new PendingSpan(circuitBreaker, request, instrumenter().start(parentContext, request));
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      pendingSpans.set(spans);
    }
    spans.push(pendingSpan);
    addGlobalPendingSpan(circuitBreaker, pendingSpan);
  }

  public static void reject(CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Context parentContext = Context.current();
    Resilience4jCircuitBreakerRequest request =
        Resilience4jCircuitBreakerRequest.create(circuitBreaker);
    if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      // Circuit breaker spans are internal and noisy without an existing trace.
      return;
    }
    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    instrumenter().end(context, request, "rejected", throwable);
  }

  public static void enterCircuitBreakerCallback() {
    inCircuitBreakerCallback.set(Boolean.TRUE);
  }

  public static void exitCircuitBreakerCallback() {
    inCircuitBreakerCallback.remove();
  }

  public static boolean isInCircuitBreakerCallback() {
    return Boolean.TRUE.equals(inCircuitBreakerCallback.get());
  }

  public static void enterOnResult() {
    Deque<Boolean> ended = onResultEnded.get();
    if (ended == null) {
      ended = new ArrayDeque<>();
      onResultEnded.set(ended);
    }
    ended.push(Boolean.FALSE);
  }

  public static boolean exitOnResult() {
    Deque<Boolean> ended = onResultEnded.get();
    if (ended == null) {
      return false;
    }
    Boolean result = ended.poll();
    if (ended.isEmpty()) {
      onResultEnded.remove();
    }
    return Boolean.TRUE.equals(result);
  }

  public static void end(
      CircuitBreaker circuitBreaker, String outcome, @Nullable Throwable throwable) {
    PendingSpan pendingSpan = pollPendingSpan(circuitBreaker);
    if (pendingSpan != null) {
      pendingSpan.end(outcome, throwable);
    }
  }

  public static void endResult(
      CircuitBreaker circuitBreaker, @Nullable Object result, @Nullable Throwable throwable) {
    if (throwable != null) {
      end(circuitBreaker, "failure", throwable);
      return;
    }
    // Do not invoke Resilience4j's recordResultPredicate from instrumentation. Result predicate
    // failures are handled when Resilience4j publishes its synthetic circuit error event.
    // Otherwise, treat onResult() completion as success.
    end(circuitBreaker, "success", null);
  }

  public static void endIfResultRecordedAsFailure(
      CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Deque<Boolean> ended = onResultEnded.get();
    if (ended != null
        && !ended.isEmpty()
        && throwable != null
        // ResultRecordedAsFailureException was added in newer Resilience4j versions and is not
        // present across the full supported range, so avoid a hard reference that would break
        // muzzle on older versions.
        && "io.github.resilience4j.circuitbreaker.ResultRecordedAsFailureException"
            .equals(throwable.getClass().getName())) {
      end(circuitBreaker, "failure", null);
      ended.pop();
      ended.push(Boolean.TRUE);
    }
  }

  public static void endAfter(
      @Nullable PendingSpan baseline, String outcome, @Nullable Throwable throwable) {
    PendingSpan pendingSpan = pollPendingSpanAfter(baseline);
    if (pendingSpan != null) {
      pendingSpan.end(outcome, throwable);
    }
  }

  @Nullable
  public static PendingSpan currentPendingSpan() {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return null;
    }
    removeEnded(spans);
    return spans.peek();
  }

  public static void attachPendingSpan(PendingSpan pendingSpan) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      pendingSpans.set(spans);
    }
    spans.push(pendingSpan);
  }

  public static void detachPendingSpan(PendingSpan pendingSpan) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return;
    }
    Iterator<PendingSpan> iterator = spans.iterator();
    while (iterator.hasNext()) {
      if (iterator.next() == pendingSpan) {
        iterator.remove();
        break;
      }
    }
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
  }

  @Nullable
  public static PendingSpan pollPendingSpanAfter(@Nullable PendingSpan baseline) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return null;
    }
    removeEnded(spans);
    PendingSpan span = spans.peek();
    if (span == null || span == baseline) {
      if (spans.isEmpty()) {
        pendingSpans.remove();
      }
      return null;
    }
    span = spans.poll();
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
    removeGlobalPendingSpan(span);
    return span;
  }

  @Nullable
  private static PendingSpan pollPendingSpan(CircuitBreaker circuitBreaker) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return pollGlobalPendingSpan(circuitBreaker);
    }
    removeEnded(spans);
    PendingSpan span = pollLocalPendingSpan(circuitBreaker, spans);
    if (span != null) {
      removeGlobalPendingSpan(span);
      return span;
    }
    return pollGlobalPendingSpan(circuitBreaker);
  }

  @Nullable
  private static PendingSpan pollLocalPendingSpan(
      CircuitBreaker circuitBreaker, Deque<PendingSpan> spans) {
    Iterator<PendingSpan> iterator = spans.iterator();
    while (iterator.hasNext()) {
      PendingSpan span = iterator.next();
      if (span.ended) {
        iterator.remove();
      } else if (span.isFor(circuitBreaker)) {
        iterator.remove();
        if (spans.isEmpty()) {
          pendingSpans.remove();
        }
        return span;
      }
    }
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
    return null;
  }

  private static void addGlobalPendingSpan(CircuitBreaker circuitBreaker, PendingSpan pendingSpan) {
    synchronized (pendingSpansByCircuitBreaker) {
      pendingSpansByCircuitBreaker
          .computeIfAbsent(circuitBreaker, unused -> new ArrayDeque<>())
          .push(pendingSpan);
    }
  }

  @Nullable
  private static PendingSpan pollGlobalPendingSpan(CircuitBreaker circuitBreaker) {
    synchronized (pendingSpansByCircuitBreaker) {
      Deque<PendingSpan> spans = pendingSpansByCircuitBreaker.get(circuitBreaker);
      if (spans == null) {
        return null;
      }
      while (!spans.isEmpty()) {
        PendingSpan span = spans.pollLast();
        if (!span.ended) {
          if (spans.isEmpty()) {
            pendingSpansByCircuitBreaker.remove(circuitBreaker);
          }
          return span;
        }
      }
      pendingSpansByCircuitBreaker.remove(circuitBreaker);
      return null;
    }
  }

  private static void removeGlobalPendingSpan(PendingSpan pendingSpan) {
    CircuitBreaker circuitBreaker = pendingSpan.circuitBreaker();
    if (circuitBreaker == null) {
      return;
    }
    synchronized (pendingSpansByCircuitBreaker) {
      Deque<PendingSpan> spans = pendingSpansByCircuitBreaker.get(circuitBreaker);
      if (spans == null) {
        return;
      }
      spans.remove(pendingSpan);
      if (spans.isEmpty()) {
        pendingSpansByCircuitBreaker.remove(circuitBreaker);
      }
    }
  }

  private static void removeEnded(Deque<PendingSpan> spans) {
    while (!spans.isEmpty() && spans.peek().ended) {
      spans.poll();
    }
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // Keep a reference to enforce 0.15.0 as the minimum version.
    circuitBreaker.tryAcquirePermission();
  }

  public static class PendingSpan {
    private final WeakReference<CircuitBreaker> circuitBreaker;
    private final Resilience4jCircuitBreakerRequest request;
    private final Context context;
    private volatile boolean ended;

    private PendingSpan(
        CircuitBreaker circuitBreaker, Resilience4jCircuitBreakerRequest request, Context context) {
      this.circuitBreaker = new WeakReference<>(circuitBreaker);
      this.request = request;
      this.context = context;
    }

    public synchronized void end(String outcome, @Nullable Throwable throwable) {
      if (ended) {
        return;
      }
      ended = true;
      instrumenter().end(context, request, outcome, throwable);
    }

    boolean isFor(CircuitBreaker circuitBreaker) {
      return this.circuitBreaker.get() == circuitBreaker;
    }

    @Nullable
    CircuitBreaker circuitBreaker() {
      return circuitBreaker.get();
    }
  }

  private Resilience4jCircuitBreakerSpans() {}
}
