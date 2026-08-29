/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15.Resilience4jCircuitBreakerSingletons.instrumenter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

public class Resilience4jCircuitBreakerSpans {

  // Raw acquirePermission()/onSuccess()/onError() does not expose an attempt token, so arbitrary
  // out-of-order raw callbacks cannot be correlated safely. Decorated APIs capture the exact
  // acquisition token; raw same-thread callbacks are only best-effort for simple usage.
  private static final ThreadLocal<Deque<AttachedPendingSpan>> attachedPendingSpans =
      new ThreadLocal<>();
  private static final ThreadLocal<Deque<AttemptToken>> currentAcquisitions = new ThreadLocal<>();
  private static final ThreadLocal<Deque<AttemptToken>> recentAcquisitions = new ThreadLocal<>();
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
      addRecentAcquisition(token);
    } else {
      removeRecentAcquisition(token);
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
    return claim(peekRecentAcquisition(circuitBreaker));
  }

  @Nullable
  private static AttemptToken peekRecentAcquisition(CircuitBreaker circuitBreaker) {
    Deque<AttemptToken> tokens = recentAcquisitions.get();
    if (tokens == null) {
      return null;
    }
    for (AttemptToken token : tokens) {
      if (!token.claimed && token.pendingSpan != null && token.circuitBreaker == circuitBreaker) {
        return token;
      }
    }
    return null;
  }

  private static void addRecentAcquisition(AttemptToken token) {
    Deque<AttemptToken> tokens = recentAcquisitions.get();
    if (tokens == null) {
      tokens = new ArrayDeque<>();
      recentAcquisitions.set(tokens);
    }
    tokens.remove(token);
    tokens.push(token);
  }

  private static void removeRecentAcquisition(AttemptToken token) {
    Deque<AttemptToken> tokens = recentAcquisitions.get();
    if (tokens == null) {
      return;
    }
    tokens.remove(token);
    if (tokens.isEmpty()) {
      recentAcquisitions.remove();
    }
  }

  @Nullable
  private static PendingSpan claim(@Nullable AttemptToken token) {
    if (token == null || token.claimed || token.pendingSpan == null) {
      return null;
    }
    token.claimed = true;
    removeRecentAcquisition(token);
    detachPendingSpan(token.pendingSpan);
    return token.pendingSpan;
  }

  private static void clearRecentAcquisition(PendingSpan pendingSpan) {
    Deque<AttemptToken> tokens = recentAcquisitions.get();
    if (tokens == null) {
      return;
    }
    tokens.removeIf(token -> token.pendingSpan == pendingSpan);
    if (tokens.isEmpty()) {
      recentAcquisitions.remove();
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

    Context context = instrumenter().start(parentContext, request);
    PendingSpan pendingSpan =
        new PendingSpan(
            circuitBreaker, request, context, openDecoratedOperationScope(circuitBreaker, context));
    Deque<AttemptToken> tokens = currentAcquisitions.get();
    AttemptToken token = tokens == null ? null : tokens.peek();
    if (token != null && token.circuitBreaker == circuitBreaker) {
      token.pendingSpan = pendingSpan;
    }
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

  public static Throwable unwrapCompletionException(Throwable throwable) {
    if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
        && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }

  public static void end(
      CircuitBreaker circuitBreaker, String outcome, @Nullable Throwable throwable) {
    AttemptToken captureToken = activeCaptureToken(circuitBreaker);
    AttemptToken recentToken = peekRecentAcquisition(circuitBreaker);
    PendingSpan pendingSpan = null;
    // If user code performs a nested raw acquisition inside a decorated call and records that
    // result before the outer operation completes, prefer the newer raw acquisition. Otherwise,
    // use the active capture or attached span owned by the decorator.
    if (recentToken != null && recentToken != captureToken) {
      pendingSpan = claim(recentToken);
    }
    if (pendingSpan == null) {
      pendingSpan = claimAttachedPendingSpan(circuitBreaker);
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
    Deque<AttachedPendingSpan> spans = attachedPendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      attachedPendingSpans.set(spans);
    }
    spans.push(new AttachedPendingSpan(pendingSpan, pendingSpan.makeCurrent()));
  }

  public static void detachPendingSpan(PendingSpan pendingSpan) {
    removeAttachedPendingSpan(pendingSpan);
  }

  private static void removeAttachedPendingSpan(PendingSpan pendingSpan) {
    Deque<AttachedPendingSpan> spans = attachedPendingSpans.get();
    if (spans == null) {
      return;
    }
    Iterator<AttachedPendingSpan> iterator = spans.iterator();
    while (iterator.hasNext()) {
      AttachedPendingSpan attachedPendingSpan = iterator.next();
      if (attachedPendingSpan.pendingSpan == pendingSpan) {
        iterator.remove();
        attachedPendingSpan.scope.close();
        break;
      }
    }
    if (spans.isEmpty()) {
      attachedPendingSpans.remove();
    }
  }

  @Nullable
  private static PendingSpan claimAttachedPendingSpan(CircuitBreaker circuitBreaker) {
    Deque<AttachedPendingSpan> spans = attachedPendingSpans.get();
    if (spans == null) {
      return null;
    }
    for (AttachedPendingSpan attachedPendingSpan : spans) {
      PendingSpan pendingSpan = attachedPendingSpan.pendingSpan;
      if (pendingSpan.isFor(circuitBreaker)) {
        detachPendingSpan(pendingSpan);
        return pendingSpan;
      }
    }
    return null;
  }

  @Nullable
  private static Scope openDecoratedOperationScope(CircuitBreaker circuitBreaker, Context context) {
    // Raw acquirePermission()/onSuccess()/onError() has no lexical boundary where a scope can be
    // closed reliably. Only decorated APIs install a capture around the protected operation, so
    // only
    // those acquisitions make the CircuitBreaker span current here. Async decorators close this
    // operation scope when handing the PendingSpan to the async result and reopen short callback
    // scopes through attachPendingSpan().
    Deque<Capture> captureStack = captures.get();
    if (captureStack == null) {
      return null;
    }
    for (Capture capture : captureStack) {
      if (capture.circuitBreaker == circuitBreaker) {
        return context.makeCurrent();
      }
    }
    return null;
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

  private static class AttachedPendingSpan {
    private final PendingSpan pendingSpan;
    private final Scope scope;

    private AttachedPendingSpan(PendingSpan pendingSpan, Scope scope) {
      this.pendingSpan = pendingSpan;
      this.scope = scope;
    }
  }

  public static class PendingSpan {
    private final CircuitBreaker circuitBreaker;
    private final Resilience4jCircuitBreakerRequest request;
    private final Context context;
    @Nullable private Scope operationScope;
    private volatile boolean ended;

    private PendingSpan(
        CircuitBreaker circuitBreaker,
        Resilience4jCircuitBreakerRequest request,
        Context context,
        @Nullable Scope operationScope) {
      this.circuitBreaker = circuitBreaker;
      this.request = request;
      this.context = context;
      this.operationScope = operationScope;
    }

    private boolean isFor(CircuitBreaker circuitBreaker) {
      return this.circuitBreaker == circuitBreaker;
    }

    public Scope makeCurrent() {
      return context.makeCurrent();
    }

    public synchronized void closeOperationScope() {
      if (operationScope != null) {
        operationScope.close();
        operationScope = null;
      }
    }

    public synchronized void end(String outcome, @Nullable Throwable throwable) {
      if (ended) {
        return;
      }
      ended = true;
      clearRecentAcquisition(this);
      closeOperationScope();
      instrumenter().end(context, request, outcome, throwable);
    }
  }

  private Resilience4jCircuitBreakerSpans() {}
}
