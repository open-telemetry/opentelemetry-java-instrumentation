package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import org.checkerframework.checker.lock.qual.GuardedBy;

public class TracingAssembly {

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super Observable, ? extends Observable> oldOnObservableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super ConnectableObservable, ? extends ConnectableObservable>
      oldOnConnectableObservableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super Completable, ? extends Completable> oldOnCompletableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super Single, ? extends Single> oldOnSingleAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super Maybe, ? extends Maybe> oldOnMaybeAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super Flowable, ? extends Flowable> oldOnFlowableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
  private static Function<? super ConnectableFlowable, ? extends ConnectableFlowable>
      oldOnConnectableFlowableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("RequestContextAssembly.class")
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

    oldOnObservableAssembly = RxJavaPlugins.getOnObservableAssembly();
    RxJavaPlugins.setOnObservableAssembly(
        compose(
            oldOnObservableAssembly,
            new ConditionalOnCurrentContextFunction<Observable>() {
              @Override
              Observable applyActual(Observable observable, Context ctx) {
                return new TracingObservable(observable, ctx);
              }
            }));

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

    oldOnCompletableAssembly = RxJavaPlugins.getOnCompletableAssembly();
    RxJavaPlugins.setOnCompletableAssembly(
        compose(
            oldOnCompletableAssembly,
            new ConditionalOnCurrentContextFunction<Completable>() {

              @Override
              Completable applyActual(Completable completable, Context ctx) {
                return new TracingCompletable(completable, ctx);
              }
            }));

    oldOnSingleAssembly = RxJavaPlugins.getOnSingleAssembly();
    RxJavaPlugins.setOnSingleAssembly(
        compose(
            oldOnSingleAssembly,
            new ConditionalOnCurrentContextFunction<Single>() {
              @Override
              Single applyActual(Single s, Context ctx) {
                return new TracingSingle(s, ctx);
              }
            }));

    oldOnMaybeAssembly = RxJavaPlugins.getOnMaybeAssembly();
    RxJavaPlugins.setOnMaybeAssembly(
        compose(
            oldOnMaybeAssembly,
            new ConditionalOnCurrentContextFunction<Maybe>() {

              @Override
              Maybe applyActual(Maybe m, Context ctx) {
                return new TracingMaybe(m, ctx);
              }
            }));

    oldOnFlowableAssembly = RxJavaPlugins.getOnFlowableAssembly();
    RxJavaPlugins.setOnFlowableAssembly(
        compose(
            oldOnFlowableAssembly,
            new ConditionalOnCurrentContextFunction<Flowable>() {

              @Override
              Flowable applyActual(Flowable flowable, Context ctx) {
                return new TracingFlowable(flowable, ctx);
              }
            }));

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
  }

  public static synchronized void disable() {
    if (!enabled) {
      return;
    }

    RxJavaPlugins.setOnSingleAssembly(oldOnSingleAssembly);
    oldOnSingleAssembly = null;

    RxJavaPlugins.setOnMaybeAssembly(oldOnMaybeAssembly);
    oldOnMaybeAssembly = null;

    RxJavaPlugins.setOnFlowableAssembly(oldOnFlowableAssembly);
    oldOnFlowableAssembly = null;

    RxJavaPlugins.setOnParallelAssembly(oldOnParallelAssembly);
    oldOnParallelAssembly = null;

    RxJavaPlugins.setOnObservableAssembly(oldOnObservableAssembly);
    oldOnObservableAssembly = null;

    RxJavaPlugins.setOnConnectableObservableAssembly(oldOnConnectableObservableAssembly);
    oldOnConnectableObservableAssembly = null;

    RxJavaPlugins.setOnCompletableAssembly(oldOnCompletableAssembly);
    oldOnCompletableAssembly = null;

    RxJavaPlugins.setOnConnectableFlowableAssembly(oldOnConnectableFlowableAssembly);
    oldOnConnectableFlowableAssembly = null;

    enabled = false;
  }

  private abstract static class ConditionalOnCurrentContextFunction<T> implements Function<T, T> {
    @Override
    public final T apply(T t) {
      return applyActual(t, Context.current());
    }

    abstract T applyActual(T t, Context ctx);
  }

  private static <T> Function<? super T, ? extends T> compose(
      Function<? super T, ? extends T> before, Function<? super T, ? extends T> after) {
    if (before == null) {
      return after;
    }
    return (T v) -> after.apply(before.apply(v));
  }
}
