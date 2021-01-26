package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public final class TracingObserver<T> implements Observer<T> {

  private final Observer<T> observer;
  private final Context parentSpan;

  public TracingObserver(final Observer<T> observer, final Context parentSpan) {
    this.observer = observer;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onNext(final T t) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onNext(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onComplete();
    }
  }
}
