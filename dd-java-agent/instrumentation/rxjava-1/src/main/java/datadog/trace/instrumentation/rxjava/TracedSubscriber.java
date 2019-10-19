package datadog.trace.instrumentation.rxjava;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {

  private final AtomicReference<AgentSpan> spanRef;
  private final Subscriber<T> delegate;
  private final BaseDecorator decorator;

  public TracedSubscriber(
      final AgentSpan span, final Subscriber<T> delegate, final BaseDecorator decorator) {
    spanRef = new AtomicReference<>(span);
    this.delegate = delegate;
    this.decorator = decorator;
    final SpanFinishingSubscription subscription =
        new SpanFinishingSubscription(decorator, spanRef);
    delegate.add(subscription);
  }

  @Override
  public void onStart() {
    final AgentSpan span = spanRef.get();
    if (span != null) {
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate.onStart();
      }
    } else {
      delegate.onStart();
    }
  }

  @Override
  public void onNext(final T value) {
    final AgentSpan span = spanRef.get();
    if (span != null) {
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate.onNext(value);
      } catch (final Throwable e) {
        onError(e);
      }
    } else {
      delegate.onNext(value);
    }
  }

  @Override
  public void onCompleted() {
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      boolean errored = false;
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate.onCompleted();
      } catch (final Throwable e) {
        // Repopulate the spanRef for onError
        spanRef.compareAndSet(null, span);
        onError(e);
        errored = true;
      } finally {
        // finish called by onError, so don't finish again.
        if (!errored) {
          decorator.beforeFinish(span);
          span.finish();
        }
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(final Throwable e) {
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        decorator.onError(span, e);
        delegate.onError(e);
      } catch (final Throwable e2) {
        decorator.onError(span, e2);
        throw e2;
      } finally {
        decorator.beforeFinish(span);
        span.finish();
      }
    } else {
      delegate.onError(e);
    }
  }
}
