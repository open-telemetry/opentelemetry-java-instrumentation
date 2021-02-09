/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.reactivestreams.Subscriber;

public class TracingAssembly {

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super Observable, ? extends Observable> oldOnObservableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Observable, ? super Observer, ? extends Observer>
      oldOnObservableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ConnectableObservable, ? extends ConnectableObservable>
      oldOnConnectableObservableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<
          ? super Completable, ? super CompletableObserver, ? extends CompletableObserver>
      oldOnCompletableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Single, ? super SingleObserver, ? extends SingleObserver>
      oldOnSingleSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Maybe, ? super MaybeObserver, ? extends MaybeObserver>
      oldOnMaybeSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Flowable, ? super Subscriber, ? extends Subscriber>
      oldOnFlowableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ConnectableFlowable, ? extends ConnectableFlowable>
      oldOnConnectableFlowableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ParallelFlowable, ? extends ParallelFlowable>
      oldOnParallelAssembly;

  @GuardedBy("TracingAssembly.class")
  private static boolean enabled;

  private TracingAssembly() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static synchronized void enable() {
    if (enabled) {
      return;
    }

    enableObservable();

    oldOnConnectableObservableAssembly = RxJavaPlugins.getOnConnectableObservableAssembly();
    RxJavaPlugins.setOnConnectableObservableAssembly(
        compose(
            oldOnConnectableObservableAssembly,
            new ConditionalOnCurrentContextFunction<ConnectableObservable>() {
              @Override
              ConnectableObservable applyActual(
                  ConnectableObservable connectableObservable, Context ctx) {
                return new TracingConnectableObservable(connectableObservable, ctx);
              }
            }));

    enableCompletable();

    enableSingle();

    enableMaybe();

    enableFlowable();

    oldOnConnectableFlowableAssembly = RxJavaPlugins.getOnConnectableFlowableAssembly();
    RxJavaPlugins.setOnConnectableFlowableAssembly(
        compose(
            oldOnConnectableFlowableAssembly,
            new ConditionalOnCurrentContextFunction<ConnectableFlowable>() {
              @Override
              ConnectableFlowable applyActual(
                  ConnectableFlowable connectableFlowable, Context ctx) {
                return new TracingConnectableFlowable(connectableFlowable, ctx);
              }
            }));

    oldOnParallelAssembly = RxJavaPlugins.getOnParallelAssembly();
    RxJavaPlugins.setOnParallelAssembly(
        compose(
            oldOnParallelAssembly,
            new ConditionalOnCurrentContextFunction<ParallelFlowable>() {
              @Override
              ParallelFlowable applyActual(ParallelFlowable parallelFlowable, Context ctx) {
                return new TracingParallelFlowable(parallelFlowable, ctx);
              }
            }));

    enabled = true;
  }

  private static synchronized void enableCompletable() {
    oldOnCompletableSubscribe = RxJavaPlugins.getOnCompletableSubscribe();
    RxJavaPlugins.setOnCompletableSubscribe(
        biCompose(
            oldOnCompletableSubscribe,
            new BiConditionalOnCurrentContextFunction<Completable, CompletableObserver>() {
              @Override
              CompletableObserver applyActual(
                  Completable completable, CompletableObserver observer, Context ctx) {
                try (final Scope scope = ctx.makeCurrent()) {
                  return new TracingCompletableObserver(observer, ctx);
                }
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static synchronized void enableFlowable() {
    oldOnFlowableSubscribe = RxJavaPlugins.getOnFlowableSubscribe();
    RxJavaPlugins.setOnFlowableSubscribe(
        biCompose(
            oldOnFlowableSubscribe,
            new BiConditionalOnCurrentContextFunction<Flowable, Subscriber>() {

              @Override
              Subscriber applyActual(Flowable flowable, Subscriber subscriber, Context ctx) {
                try (final Scope scope = ctx.makeCurrent()) {
                  if (subscriber instanceof ConditionalSubscriber) {
                    return new TracingConditionalSubscriber<>(
                        (ConditionalSubscriber) subscriber, ctx);
                  } else {
                    return new TracingSubscriber<>(subscriber, ctx);
                  }
                }
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static synchronized void enableObservable() {
    oldOnObservableSubscribe = RxJavaPlugins.getOnObservableSubscribe();
    RxJavaPlugins.setOnObservableSubscribe(
        biCompose(
            oldOnObservableSubscribe,
            new BiConditionalOnCurrentContextFunction<Observable, Observer>() {

              @Override
              Observer applyActual(Observable observable, Observer observer, Context ctx) {
                try (final Scope scope = ctx.makeCurrent()) {
                  return new TracingObserver(observer, ctx);
                }
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static synchronized void enableSingle() {
    oldOnSingleSubscribe = RxJavaPlugins.getOnSingleSubscribe();
    RxJavaPlugins.setOnSingleSubscribe(
        biCompose(
            oldOnSingleSubscribe,
            new BiConditionalOnCurrentContextFunction<Single, SingleObserver>() {

              @Override
              SingleObserver applyActual(
                  Single single, SingleObserver singleObserver, Context ctx) {
                try (final Scope scope = ctx.makeCurrent()) {
                  return new TracingSingleObserver(singleObserver, ctx);
                }
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static synchronized void enableMaybe() {
    oldOnMaybeSubscribe = RxJavaPlugins.getOnMaybeSubscribe();
    RxJavaPlugins.setOnMaybeSubscribe(
        (BiFunction<? super Maybe, MaybeObserver, ? extends MaybeObserver>)
            biCompose(
                oldOnMaybeSubscribe,
                new BiConditionalOnCurrentContextFunction<Maybe, MaybeObserver>() {

                  @Override
                  MaybeObserver applyActual(Maybe maybe, MaybeObserver maybeObserver, Context ctx) {
                    try (final Scope scope = ctx.makeCurrent()) {
                      return new TracingMaybeObserver(maybeObserver, ctx);
                    }
                  }
                }));
  }

  public static synchronized void disable() {
    if (!enabled) {
      return;
    }

    disableSingle();

    disableMaybe();

    disableFlowable();

    RxJavaPlugins.setOnParallelAssembly(oldOnParallelAssembly);
    oldOnParallelAssembly = null;

    disableObservable();

    RxJavaPlugins.setOnConnectableObservableAssembly(oldOnConnectableObservableAssembly);
    oldOnConnectableObservableAssembly = null;

    disableCompletable();

    RxJavaPlugins.setOnConnectableFlowableAssembly(oldOnConnectableFlowableAssembly);
    oldOnConnectableFlowableAssembly = null;

    enabled = false;
  }

  private static synchronized void disableObservable() {
    RxJavaPlugins.setOnObservableAssembly(oldOnObservableAssembly);
    oldOnObservableAssembly = null;
    RxJavaPlugins.setOnObservableSubscribe(oldOnObservableSubscribe);
    oldOnObservableSubscribe = null;
  }

  private static synchronized void disableCompletable() {
    RxJavaPlugins.setOnCompletableSubscribe(oldOnCompletableSubscribe);
    oldOnCompletableSubscribe = null;
  }

  private static synchronized void disableFlowable() {
    RxJavaPlugins.setOnFlowableSubscribe(oldOnFlowableSubscribe);
    oldOnFlowableSubscribe = null;
  }

  private static synchronized void disableSingle() {
    RxJavaPlugins.setOnSingleSubscribe(oldOnSingleSubscribe);
    oldOnSingleSubscribe = null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static synchronized void disableMaybe() {
    RxJavaPlugins.setOnMaybeSubscribe(
        (BiFunction<? super Maybe, MaybeObserver, ? extends MaybeObserver>) oldOnMaybeSubscribe);
    oldOnMaybeSubscribe = null;
  }

  private abstract static class ConditionalOnCurrentContextFunction<T> implements Function<T, T> {
    @Override
    public final T apply(T t) {
      return applyActual(t, Context.current());
    }

    abstract T applyActual(T t, Context ctx);
  }

  private abstract static class BiConditionalOnCurrentContextFunction<T, U>
      implements BiFunction<T, U, U> {
    @Override
    public U apply(@NonNull T t, @NonNull U u) {
      return applyActual(t, u, Context.current());
    }

    abstract U applyActual(T t, U u, Context ctx);
  }

  private static <T> Function<? super T, ? extends T> compose(
      Function<? super T, ? extends T> before, Function<? super T, ? extends T> after) {
    if (before == null) {
      return after;
    }
    return (T v) -> after.apply(before.apply(v));
  }

  private static <T, U> BiFunction<? super T, ? super U, ? extends U> biCompose(
      BiFunction<? super T, ? super U, ? extends U> before,
      BiFunction<? super T, ? super U, ? extends U> after) {
    if (before == null) {
      return after;
    }
    return (T v, U u) -> after.apply(v, before.apply(v, u));
  }
}
