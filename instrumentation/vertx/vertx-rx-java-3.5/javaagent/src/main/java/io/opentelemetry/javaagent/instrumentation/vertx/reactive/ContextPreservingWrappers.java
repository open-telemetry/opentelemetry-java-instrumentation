package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.Disposable;

public final class ContextPreservingWrappers {

  private ContextPreservingWrappers() {}

  @SuppressWarnings("unchecked")
  public static Object wrapObserverIfNeeded(Object observer, Context ctx) {
    if (observer instanceof SingleObserver) {
      return new SingleObserverWrapper<>((SingleObserver<?>) observer, ctx);
    }
    if (observer instanceof CompletableObserver) {
      return new CompletableObserverWrapper((CompletableObserver) observer, ctx);
    }
    if (observer instanceof Observer) {
      return new ObserverWrapper<>((Observer<?>) observer, ctx);
    }
    // fallback for other callback types
    return observer;
  }

  static final class ObserverWrapper<T> implements Observer<T> {
    private final Observer<T> delegate;
    private final Context context;

    ObserverWrapper(Observer<T> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public void onSubscribe(Disposable d) {
      try (Scope s = context.makeCurrent()) {
        delegate.onSubscribe(d);
      }
    }

    @Override
    public void onNext(T t) {
      try (Scope s = context.makeCurrent()) {
        delegate.onNext(t);
      }
    }

    @Override
    public void onError(Throwable e) {
      try (Scope s = context.makeCurrent()) {
        delegate.onError(e);
      }
    }

    @Override
    public void onComplete() {
      try (Scope s = context.makeCurrent()) {
        delegate.onComplete();
      }
    }
  }

  static final class SingleObserverWrapper<T> implements SingleObserver<T> {
    private final SingleObserver<T> delegate;
    private final Context context;

    SingleObserverWrapper(SingleObserver<T> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public void onSubscribe(Disposable d) {
      try (Scope s = context.makeCurrent()) {
        delegate.onSubscribe(d);
      }
    }

    @Override
    public void onSuccess(T t) {
      try (Scope s = context.makeCurrent()) {
        delegate.onSuccess(t);
      }
    }

    @Override
    public void onError(Throwable e) {
      try (Scope s = context.makeCurrent()) {
        delegate.onError(e);
      }
    }
  }

  static final class CompletableObserverWrapper implements CompletableObserver {
    private final CompletableObserver delegate;
    private final Context context;

    CompletableObserverWrapper(CompletableObserver delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public void onSubscribe(Disposable d) {
      try (Scope s = context.makeCurrent()) {
        delegate.onSubscribe(d);
      }
    }

    @Override
    public void onComplete() {
      try (Scope s = context.makeCurrent()) {
        delegate.onComplete();
      }
    }

    @Override
    public void onError(Throwable e) {
      try (Scope s = context.makeCurrent()) {
        delegate.onError(e);
      }
    }
  }
}
