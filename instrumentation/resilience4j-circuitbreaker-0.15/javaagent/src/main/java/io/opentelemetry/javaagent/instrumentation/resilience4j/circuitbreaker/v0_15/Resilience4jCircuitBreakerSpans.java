/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15.Resilience4jCircuitBreakerSingletons.instrumenter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.annotation.Nullable;

public class Resilience4jCircuitBreakerSpans {

  // Raw acquirePermission()/onSuccess()/onError() does not expose an attempt token, so arbitrary
  // out-of-order raw callbacks cannot be correlated safely. Decorated APIs capture the exact
  // acquisition token; raw same-thread callbacks are only best-effort for simple usage.
  private static final ThreadLocal<Deque<PendingSpan>> pendingSpans = new ThreadLocal<>();
  private static final ThreadLocal<Deque<AttemptToken>> currentAcquisitions = new ThreadLocal<>();
  private static final ThreadLocal<AttemptToken> recentAcquisition = new ThreadLocal<>();
  private static final ThreadLocal<Deque<Capture>> captures = new ThreadLocal<>();
  private static final ThreadLocal<Deque<CircuitBreaker>> circuitBreakerCallbacks =
      new ThreadLocal<>();
  private static final ThreadLocal<Deque<OnResult>> onResults = new ThreadLocal<>();

  public static AttemptToken beginAcquisition(CircuitBreaker circuitBreaker) {
    AttemptToken token = new AttemptToken(circuitBreaker);
    Deque<AttemptToken> tokens = currentAcquisitions.get();
    if (tokens == null) {
      tokens = new ArrayDeque<>();
      currentAcquisitions.set(tokens);
    }
    tokens.push(token);
    recentAcquisition.remove();
    return token;
  }

  public static void finishAcquisition(@Nullable AttemptToken token) {
    Deque<AttemptToken> tokens = currentAcquisitions.get();
    if (token == null) {
      currentAcquisitions.remove();
      return;
    }
    if (tokens != null) {
      if (tokens.peek() == token) {
        tokens.poll();
      } else {
        tokens.remove(token);
      }
      if (tokens.isEmpty()) {
        currentAcquisitions.remove();
      }
    }
    if (token.pendingSpan != null && !token.claimed) {
      recentAcquisition.set(token);
    } else if (recentAcquisition.get() == token) {
      recentAcquisition.remove();
    }
    Deque<Capture> captureStack = captures.get();
    if (captureStack != null && !captureStack.isEmpty()) {
      Capture capture = captureStack.peek();
      if (capture.circuitBreaker == token.circuitBreaker && capture.token == null) {
        capture.token = token;
      }
    }
  }

  public static Capture beginCapture(CircuitBreaker circuitBreaker) {
    Capture capture = new Capture(circuitBreaker);
    Deque<Capture> captureStack = captures.get();
    if (captureStack == null) {
      captureStack = new ArrayDeque<>();
      captures.set(captureStack);
    }
    captureStack.push(capture);
    return capture;
  }

  @Nullable
  public static PendingSpan endCapture(Capture capture) {
    Deque<Capture> captureStack = captures.get();
    if (captureStack != null) {
      if (captureStack.peek() == capture) {
        captureStack.poll();
      } else {
        captureStack.remove(capture);
      }
      if (captureStack.isEmpty()) {
        captures.remove();
      }
    }
    return claim(capture.token);
  }

  @Nullable
  public static PendingSpan claimRecentAcquisition(CircuitBreaker circuitBreaker) {
    AttemptToken token = recentAcquisition.get();
    if (token == null || token.circuitBreaker != circuitBreaker) {
      return null;
    }
    recentAcquisition.remove();
    return claim(token);
  }

  @Nullable
  private static PendingSpan claim(@Nullable AttemptToken token) {
    if (token == null || token.claimed || token.pendingSpan == null) {
      return null;
    }
    token.claimed = true;
    if (recentAcquisition.get() == token) {
      recentAcquisition.remove();
    }
    detachPendingSpan(token.pendingSpan);
    return token.pendingSpan;
  }

  private static void clearRecentAcquisition(PendingSpan pendingSpan) {
    AttemptToken token = recentAcquisition.get();
    if (token != null && token.pendingSpan == pendingSpan) {
      recentAcquisition.remove();
    }
  }

  public static void start(CircuitBreaker circuitBreaker) {
    Context parentContext = Context.current();
    if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      // Circuit breaker spans are internal and noisy without an existing trace.
      return;
    }
    Resilience4jCircuitBreakerRequest request =
        Resilience4jCircuitBreakerRequest.create(circuitBreaker);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    PendingSpan pendingSpan =
        new PendingSpan(request, instrumenter().start(parentContext, request));
    Deque<AttemptToken> tokens = currentAcquisitions.get();
    AttemptToken token = tokens == null ? null : tokens.peek();
    if (token != null && token.circuitBreaker == circuitBreaker) {
      token.pendingSpan = pendingSpan;
    }
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      pendingSpans.set(spans);
    }
    spans.push(pendingSpan);
  }

  public static void reject(CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Context parentContext = Context.current();
    if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      // Circuit breaker spans are internal and noisy without an existing trace.
      return;
    }
    Resilience4jCircuitBreakerRequest request =
        Resilience4jCircuitBreakerRequest.create(circuitBreaker);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    instrumenter().end(context, request, "rejected", throwable);
  }

  public static void enterCircuitBreakerCallback(CircuitBreaker circuitBreaker) {
    Deque<CircuitBreaker> callbacks = circuitBreakerCallbacks.get();
    if (callbacks == null) {
      callbacks = new ArrayDeque<>();
      circuitBreakerCallbacks.set(callbacks);
    }
    callbacks.push(circuitBreaker);
  }

  public static void exitCircuitBreakerCallback(CircuitBreaker circuitBreaker) {
    Deque<CircuitBreaker> callbacks = circuitBreakerCallbacks.get();
    if (callbacks == null) {
      return;
    }
    if (callbacks.peek() == circuitBreaker) {
      callbacks.poll();
    } else {
      callbacks.remove(circuitBreaker);
    }
    if (callbacks.isEmpty()) {
      circuitBreakerCallbacks.remove();
    }
  }

  public static boolean isInCircuitBreakerCallback(CircuitBreaker circuitBreaker) {
    Deque<CircuitBreaker> callbacks = circuitBreakerCallbacks.get();
    return callbacks != null && callbacks.peek() == circuitBreaker;
  }

  public static void enterOnResult(CircuitBreaker circuitBreaker) {
    Deque<OnResult> results = onResults.get();
    if (results == null) {
      results = new ArrayDeque<>();
      onResults.set(results);
    }
    results.push(new OnResult(circuitBreaker));
  }

  public static boolean isOnResultActive(CircuitBreaker circuitBreaker) {
    Deque<OnResult> results = onResults.get();
    return results != null && !results.isEmpty() && results.peek().circuitBreaker == circuitBreaker;
  }

  public static boolean exitOnResult() {
    Deque<OnResult> results = onResults.get();
    if (results == null) {
      return false;
    }
    OnResult result = results.poll();
    if (results.isEmpty()) {
      onResults.remove();
    }
    return result != null && result.ended;
  }

  public static void end(
      CircuitBreaker circuitBreaker, String outcome, @Nullable Throwable throwable) {
    AttemptToken captureToken = activeCaptureToken(circuitBreaker);
    AttemptToken recentToken = recentAcquisition.get();
    PendingSpan pendingSpan = null;
    // If user code performs a nested raw acquisition inside a decorated call and records that
    // result before the decorated call completes, prefer the newer raw acquisition. Otherwise, use
    // the active capture token owned by the decorator.
    if (recentToken != null
        && recentToken.circuitBreaker == circuitBreaker
        && recentToken != captureToken) {
      pendingSpan = claim(recentToken);
    }
    if (pendingSpan == null) {
      pendingSpan = claim(captureToken);
    }
    if (pendingSpan == null) {
      pendingSpan = claimRecentAcquisition(circuitBreaker);
    }
    if (pendingSpan != null) {
      pendingSpan.end(outcome, throwable);
    }
  }

  public static void endResult(CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
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
    Deque<OnResult> results = onResults.get();
    if (results != null
        && !results.isEmpty()
        && results.peek().circuitBreaker == circuitBreaker
        && throwable != null
        // ResultRecordedAsFailureException was added in newer Resilience4j versions and is not
        // present across the full supported range, so avoid a hard reference that would break
        // muzzle on older versions.
        && "io.github.resilience4j.circuitbreaker.ResultRecordedAsFailureException"
            .equals(throwable.getClass().getName())) {
      end(circuitBreaker, "failure", null);
      results.peek().ended = true;
    }
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
  private static AttemptToken activeCaptureToken(CircuitBreaker circuitBreaker) {
    Deque<Capture> captureStack = captures.get();
    if (captureStack == null) {
      return null;
    }
    for (Capture capture : captureStack) {
      AttemptToken token = capture.token;
      if (token != null && token.circuitBreaker == circuitBreaker) {
        return token;
      }
    }
    return null;
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // Keep a reference to enforce 0.15.0 as the minimum version.
    circuitBreaker.tryAcquirePermission();
  }

  public static class AttemptToken {
    private final CircuitBreaker circuitBreaker;
    @Nullable private PendingSpan pendingSpan;
    private boolean claimed;

    private AttemptToken(CircuitBreaker circuitBreaker) {
      this.circuitBreaker = circuitBreaker;
    }
  }

  public static class Capture {
    private final CircuitBreaker circuitBreaker;
    @Nullable private AttemptToken token;

    private Capture(CircuitBreaker circuitBreaker) {
      this.circuitBreaker = circuitBreaker;
    }
  }

  private static class OnResult {
    private final CircuitBreaker circuitBreaker;
    private boolean ended;

    private OnResult(CircuitBreaker circuitBreaker) {
      this.circuitBreaker = circuitBreaker;
    }
  }

  public static class PendingSpan {
    private final Resilience4jCircuitBreakerRequest request;
    private final Context context;
    private volatile boolean ended;

    private PendingSpan(Resilience4jCircuitBreakerRequest request, Context context) {
      this.request = request;
      this.context = context;
    }

    public synchronized void end(String outcome, @Nullable Throwable throwable) {
      if (ended) {
        return;
      }
      ended = true;
      clearRecentAcquisition(this);
      instrumenter().end(context, request, outcome, throwable);
    }
  }

  private Resilience4jCircuitBreakerSpans() {}
}
