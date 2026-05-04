/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;
import rx.OpenTelemetryTracingUtil;
import rx.Subscriber;

class CouchbaseConfigurationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.config.loader.AbstractLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("loadConfig"))
            .and(takesArguments(4))
            .and(returns(named("rx.Observable"))),
        getClass().getName() + "$LoadConfigAdvice");
  }

  @SuppressWarnings("unused")
  public static class LoadConfigAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Observable<?> onExit(@Advice.Return Observable<?> result) {
      return Observable.create(
          new ContextPropagatingOnSubscribe<>(result, Java8BytecodeBridge.currentContext()));
    }
  }

  public static class ContextPropagatingOnSubscribe<T> implements Observable.OnSubscribe<T> {
    private final Observable.OnSubscribe<T> delegate;
    private final Context context;

    public ContextPropagatingOnSubscribe(Observable<T> originalObservable, Context context) {
      this.delegate = OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
      this.context = context;
    }

    @Override
    public void call(Subscriber<? super T> subscriber) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.call(new ContextPropagatingSubscriber<>(subscriber, context));
      }
    }
  }

  public static class ContextPropagatingSubscriber<T> extends Subscriber<T> {
    private final Subscriber<? super T> delegate;
    private final Context context;

    public ContextPropagatingSubscriber(Subscriber<? super T> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public void onStart() {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onStart();
      }
    }

    @Override
    public void onNext(T value) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onNext(value);
      }
    }

    @Override
    public void onCompleted() {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onCompleted();
      }
    }

    @Override
    public void onError(Throwable e) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onError(e);
      }
    }
  }
}