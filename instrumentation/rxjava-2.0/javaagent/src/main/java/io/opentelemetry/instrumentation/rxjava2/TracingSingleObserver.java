package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

public final class TracingSingleObserver<T> implements SingleObserver<T> {

  private final SingleObserver<T> observer;
  private final Context parentSpan;

  public TracingSingleObserver(final SingleObserver<T> observer, final Context parentSpan) {
    this.observer = observer;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onSuccess(final T t) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onSuccess(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onError(throwable);
    }
  }
}
