/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0.Resilience4jCircuitBreakerSingletons.instrumenter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.ResultRecordedAsFailureException;
import io.github.resilience4j.core.functions.CheckedSupplier;
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

  private static final ThreadLocal<Deque<AttachedPendingSpan>> attachedPendingSpans =
      new ThreadLocal<>();
  private static final ThreadLocal<Deque<Capture>> captures = new ThreadLocal<>();
  private static final ThreadLocal<Deque<OnResult>> onResults = new ThreadLocal<>();

  @Nullable
  public static AttemptToken beginAcquisition(CircuitBreaker circuitBreaker) {
    Deque<Capture> captureStack = captures.get();
    Capture capture = captureStack == null ? null : captureStack.peek();
    if (capture == null || capture.circuitBreaker != circuitBreaker || capture.token != null) {
      return null;
    }
    AttemptToken token = new AttemptToken();
    capture.token = token;
    return token;
  }

  static Capture beginCapture(CircuitBreaker circuitBreaker) {
    Capture capture = new Capture(circuitBreaker);
    Deque<Capture> captureStack = captures.get();
    if (captureStack == null) {
      captureStack = new ArrayDeque<>();
      captures.set(captureStack);
    }
    captureStack.push(capture);
    return capture;
  }

  static Capture beginCaptureAfterAcquisition(CircuitBreaker circuitBreaker) {
    Deque<Capture> captureStack = captures.get();
    Capture outer = captureStack == null ? null : captureStack.peek();
    Capture capture = beginCapture(circuitBreaker);
    if (outer != null && outer.circuitBreaker == circuitBreaker) {
      capture.token = outer.token;
    }
    capture.userCodeDepth = 1;
    return capture;
  }

  @Nullable
  static Capture beginUserCode(CircuitBreaker circuitBreaker) {
    Deque<Capture> captureStack = captures.get();
    Capture capture = captureStack == null ? null : captureStack.peek();
    if (capture != null && capture.circuitBreaker == circuitBreaker) {
      capture.userCodeDepth++;
      return capture;
    }
    return null;
  }

  static void endUserCode(@Nullable Capture capture) {
    if (capture != null) {
      capture.userCodeDepth--;
    }
  }

  @Nullable
  static PendingSpan endCapture(Capture capture) {
    removeCapture(capture);
    return claim(capture.token);
  }

  @Nullable
  static PendingSpan endCaptureOnFailure(Capture capture) {
    removeCapture(capture);
    PendingSpan pendingSpan = claim(capture.token);
    return pendingSpan != null
        ? pendingSpan
        : capture.token == null ? null : capture.token.pendingSpan;
  }

  static void cancelCapture(Capture capture) {
    removeCapture(capture);
  }

  private static void removeCapture(Capture capture) {
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
  }

  @Nullable
  static PendingSpan claimCapturedAcquisition(CircuitBreaker circuitBreaker) {
    Deque<Capture> captureStack = captures.get();
    Capture capture = captureStack == null ? null : captureStack.peek();
    return capture != null && capture.circuitBreaker == circuitBreaker
        ? claim(capture.token)
        : null;
  }

  @Nullable
  private static PendingSpan claim(@Nullable AttemptToken token) {
    if (token == null || token.claimed || token.pendingSpan == null) {
      return null;
    }
    token.claimed = true;
    return token.pendingSpan;
  }

  public static void start(CircuitBreaker circuitBreaker, @Nullable AttemptToken token) {
    if (token == null) {
      return;
    }
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
        new PendingSpan(circuitBreaker, request, context, context.makeCurrent());
    token.pendingSpan = pendingSpan;
  }

  public static void reject(
      CircuitBreaker circuitBreaker, @Nullable AttemptToken token, @Nullable Throwable throwable) {
    if (token == null) {
      return;
    }
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

  public static void enterOnResult(CircuitBreaker circuitBreaker) {
    Deque<OnResult> results = onResults.get();
    if (results == null) {
      results = new ArrayDeque<>();
      onResults.set(results);
    }
    results.push(new OnResult(circuitBreaker, currentPendingSpan(circuitBreaker)));
  }

  public static void endOnResult() {
    Deque<OnResult> results = onResults.get();
    OnResult result = results == null ? null : results.poll();
    if (results != null && results.isEmpty()) {
      onResults.remove();
    }
    if (result != null && result.resultRecordedAsFailure && result.pendingSpan != null) {
      result.pendingSpan.recordResultFailure();
    }
  }

  public static Throwable unwrapCompletionException(Throwable throwable) {
    if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
        && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }

  public static void endIfResultRecordedAsFailure(
      CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Deque<OnResult> results = onResults.get();
    if (results == null || results.isEmpty() || throwable == null) {
      return;
    }
    OnResult result = results.peek();
    if (result.circuitBreaker == circuitBreaker
        && result.pendingSpan != null
        && throwable instanceof ResultRecordedAsFailureException) {
      result.resultRecordedAsFailure = true;
    }
  }

  static void attachPendingSpan(PendingSpan pendingSpan) {
    Deque<AttachedPendingSpan> spans = attachedPendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      attachedPendingSpans.set(spans);
    }
    spans.push(new AttachedPendingSpan(pendingSpan, pendingSpan.makeCurrent()));
  }

  static void detachPendingSpan(PendingSpan pendingSpan) {
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
  private static PendingSpan currentPendingSpan(CircuitBreaker circuitBreaker) {
    Deque<OnResult> results = onResults.get();
    if (results != null && !results.isEmpty() && results.peek().circuitBreaker == circuitBreaker) {
      return null;
    }
    Deque<Capture> captureStack = captures.get();
    Capture capture = captureStack == null ? null : captureStack.peek();
    if (capture != null && capture.userCodeDepth > 0) {
      return null;
    }
    if (capture != null && capture.circuitBreaker == circuitBreaker && capture.token != null) {
      return capture.token.pendingSpan;
    }
    Deque<AttachedPendingSpan> spans = attachedPendingSpans.get();
    if (spans == null) {
      return null;
    }
    PendingSpan pendingSpan = spans.peek().pendingSpan;
    return pendingSpan.isFor(circuitBreaker) ? pendingSpan : null;
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // Keep a reference to enforce 2.0.0 as the minimum version.
    CircuitBreaker.decorateCheckedSupplier(circuitBreaker, (CheckedSupplier<Object>) () -> null);
  }

  public static class AttemptToken {
    @Nullable private PendingSpan pendingSpan;
    private boolean claimed;
  }

  static class Capture {
    private final CircuitBreaker circuitBreaker;
    @Nullable private AttemptToken token;
    private int userCodeDepth;

    private Capture(CircuitBreaker circuitBreaker) {
      this.circuitBreaker = circuitBreaker;
    }
  }

  private static class OnResult {
    private final CircuitBreaker circuitBreaker;
    @Nullable private final PendingSpan pendingSpan;
    private boolean resultRecordedAsFailure;

    private OnResult(CircuitBreaker circuitBreaker, @Nullable PendingSpan pendingSpan) {
      this.circuitBreaker = circuitBreaker;
      this.pendingSpan = pendingSpan;
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

  static class PendingSpan {
    private final CircuitBreaker circuitBreaker;
    private final Resilience4jCircuitBreakerRequest request;
    private final Context context;
    @Nullable private Scope operationScope;
    private boolean ended;
    private boolean resultRecordedAsFailure;

    private PendingSpan(
        CircuitBreaker circuitBreaker,
        Resilience4jCircuitBreakerRequest request,
        Context context,
        Scope operationScope) {
      this.circuitBreaker = circuitBreaker;
      this.request = request;
      this.context = context;
      this.operationScope = operationScope;
    }

    private boolean isFor(CircuitBreaker circuitBreaker) {
      return this.circuitBreaker == circuitBreaker;
    }

    Scope makeCurrent() {
      return context.makeCurrent();
    }

    void closeOperationScope() {
      Scope scope;
      synchronized (this) {
        scope = operationScope;
        operationScope = null;
      }
      if (scope != null) {
        scope.close();
      }
    }

    synchronized void recordResultFailure() {
      resultRecordedAsFailure = true;
    }

    void end(String outcome, @Nullable Throwable throwable) {
      synchronized (this) {
        if (ended) {
          return;
        }
        ended = true;
        if (resultRecordedAsFailure && "success".equals(outcome)) {
          outcome = "failure";
        }
      }
      detachPendingSpan(this);
      closeOperationScope();
      instrumenter().end(context, request, outcome, throwable);
    }
  }

  private Resilience4jCircuitBreakerSpans() {}
}
