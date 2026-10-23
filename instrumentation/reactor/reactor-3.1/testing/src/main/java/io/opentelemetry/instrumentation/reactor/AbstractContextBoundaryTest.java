/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public abstract class AbstractContextBoundaryTest {

  private static final ContextKey<String> KEY = ContextKey.named("boundary-test");
  private static final Context OPERATION = Context.root().with(KEY, "operation");
  private static final Context APPLICATION = Context.root().with(KEY, "application");
  private static final Context UNRELATED = Context.root().with(KEY, "unrelated");
  private static final Context INNER = Context.root().with(KEY, "inner");

  protected abstract Publisher<String> runWithContext(
      Publisher<String> source, Context operationContext, Context applicationContext);

  protected abstract void registerHooks();

  protected abstract void resetHooks();

  @AfterEach
  void resetContextBoundaryHooks() {
    resetHooks();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void transparentHookedDownstreamChainUsesApplicationContext(
      boolean mono, boolean rootApplication) {
    registerHooks();
    Context application = rootApplication ? Context.root() : APPLICATION;
    AtomicReference<Context> observed = new AtomicReference<>();
    try (Scope ignored = Context.root().makeCurrent()) {
      Flux.from(runWithContext(source(mono, "value"), OPERATION, application))
          .map(value -> value)
          .doOnNext(
              value -> {
                assertContext(Context.current(), application);
                observed.set(Context.current());
              })
          .collectList()
          .block();
      assertContext(Context.current(), Context.root());
    }
    assertContext(observed.get(), application);
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false",
    "false,false,true",
    "false,true,false",
    "false,true,true",
    "true,false,false",
    "true,false,true",
    "true,true,false",
    "true,true,true"
  })
  void deferredBoundaryRetainsItsCapturedContext(
      boolean mono, boolean rootApplication, boolean schedulerHop) {
    assumeSchedulerHooksSupported(schedulerHop);
    registerHooks();
    Context application = rootApplication ? Context.root() : APPLICATION;
    Context subscription = Context.root().with(KEY, "subscription");
    Scheduler scheduler = schedulerHop ? Schedulers.newSingle("context-boundary-test") : null;
    AtomicReference<Context> observed = new AtomicReference<>();
    try {
      Flux<String> boundary =
          Flux.defer(
              () -> Flux.from(runWithContext(source(mono, "value"), OPERATION, application)));
      if (schedulerHop) {
        boundary = boundary.publishOn(scheduler);
      }
      try (Scope ignored = subscription.makeCurrent()) {
        boundary
            .map(value -> value)
            .doOnNext(
                value -> {
                  observed.set(Context.current());
                  assertContext(Context.current(), subscription);
                })
            .collectList()
            .block();
        assertContext(Context.current(), subscription);
      }
    } finally {
      if (scheduler != null) {
        scheduler.dispose();
      }
    }
    assertContext(observed.get(), subscription);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void schedulerHopRetainsItsCapturedContext(boolean mono, boolean rootApplication) {
    assumeSchedulerHooksSupported(true);
    registerHooks();
    Context application = rootApplication ? Context.root() : APPLICATION;
    Scheduler scheduler = Schedulers.newSingle("context-boundary-test");
    AtomicReference<Context> observed = new AtomicReference<>();
    try {
      try (Scope ignored = UNRELATED.makeCurrent()) {
        Flux.from(runWithContext(source(mono, "value"), OPERATION, application))
            .publishOn(scheduler)
            .map(value -> value)
            .doOnNext(
                value -> {
                  assertContext(Context.current(), UNRELATED);
                  observed.set(Context.current());
                })
            .collectList()
            .block();
        assertContext(Context.current(), UNRELATED);
      }
      try (Scope ignored = INNER.makeCurrent()) {
        Mono.just("independent")
            .publishOn(scheduler)
            .doOnNext(value -> assertContext(Context.current(), INNER))
            .block();
        assertContext(Context.current(), INNER);
      }
    } finally {
      scheduler.dispose();
    }
    assertContext(observed.get(), UNRELATED);
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false",
    "false,false,true",
    "false,true,false",
    "false,true,true",
    "true,false,false",
    "true,false,true",
    "true,true,false",
    "true,true,true"
  })
  void independentPublisherKeepsItsCapturedContext(
      boolean mono, boolean schedulerHop, boolean sameContext) {
    assumeSchedulerHooksSupported(schedulerHop);
    registerHooks();
    Context independentSubscription = sameContext ? UNRELATED : INNER;
    AtomicReference<MonoSink<String>> sink = new AtomicReference<>();
    AtomicReference<Context> independentSignal = new AtomicReference<>();
    try (Scope ignored = independentSubscription.makeCurrent()) {
      Mono.<String>create(sink::set)
          .map(
              value -> {
                independentSignal.set(Context.current());
                return value;
              })
          .subscribe();
    }

    Scheduler scheduler = schedulerHop ? Schedulers.newSingle("context-boundary-test") : null;
    try {
      Flux<String> boundary =
          Flux.from(runWithContext(source(mono, "boundary"), OPERATION, APPLICATION));
      if (schedulerHop) {
        boundary = boundary.publishOn(scheduler);
      }
      try (Scope ignored = Context.root().makeCurrent()) {
        boundary
            .doOnNext(
                value -> {
                  assertContext(Context.current(), APPLICATION);
                  sink.get().success("independent");
                  assertContext(Context.current(), APPLICATION);
                })
            .collectList()
            .block();
        assertContext(Context.current(), Context.root());
      }
    } finally {
      if (scheduler != null) {
        scheduler.dispose();
      }
    }
    assertContext(independentSignal.get(), independentSubscription);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delayedDemandOutsideBoundaryRetainsItsCapturedContext(boolean rootApplication)
      throws InterruptedException {
    assumeSchedulerHooksSupported(true);
    registerHooks();
    Context application = rootApplication ? Context.root() : APPLICATION;
    Scheduler scheduler = Schedulers.newSingle("context-boundary-test");
    AtomicReference<Subscription> subscription = new AtomicReference<>();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    AtomicReference<Context> independentContext = new AtomicReference<>();
    AtomicReference<Context> contextAfterIndependentSignal = new AtomicReference<>();
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    AtomicReference<Thread> reusedWorkerThread = new AtomicReference<>();
    AtomicReference<MonoSink<String>> sink = new AtomicReference<>();
    CountDownLatch initialDrainFinished = new CountDownLatch(1);
    CountDownLatch callbackFinished = new CountDownLatch(1);
    CountDownLatch workerReused = new CountDownLatch(1);
    try {
      try (Scope ignored = INNER.makeCurrent()) {
        Mono.<String>create(sink::set)
            .map(
                value -> {
                  independentContext.set(Context.current());
                  return value;
                })
            .subscribe();
      }
      try (Scope ignored = UNRELATED.makeCurrent()) {
        Flux.from(runWithContext(Flux.just("value"), OPERATION, application))
            .publishOn(scheduler)
            .doOnNext(
                value -> {
                  callbackContext.set(Context.current());
                  callbackThread.set(Thread.currentThread());
                  sink.get().success("independent");
                  contextAfterIndependentSignal.set(Context.current());
                  scheduler.schedule(
                      () -> {
                        reusedWorkerThread.set(Thread.currentThread());
                        workerReused.countDown();
                      });
                  callbackFinished.countDown();
                })
            .subscribe(noDemandSubscriber(subscription::set));
        assertContext(Context.current(), UNRELATED);
      }

      scheduler.schedule(initialDrainFinished::countDown);
      assertThat(initialDrainFinished.await(10, SECONDS)).isTrue();
      assertThat(subscription.get()).isNotNull();
      try (Scope ignored = UNRELATED.makeCurrent()) {
        subscription.get().request(1);
        assertContext(Context.current(), UNRELATED);
      }
      assertThat(callbackFinished.await(10, SECONDS)).isTrue();
      assertThat(workerReused.await(10, SECONDS)).isTrue();
      assertContext(callbackContext.get(), UNRELATED);
      assertContext(independentContext.get(), INNER);
      assertContext(contextAfterIndependentSignal.get(), UNRELATED);
      assertThat(reusedWorkerThread.get()).isSameAs(callbackThread.get());
    } finally {
      scheduler.dispose();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delayedDemandFromAnotherBoundaryRetainsItsCapturedContext(boolean rootApplication)
      throws InterruptedException {
    assumeSchedulerHooksSupported(true);
    registerHooks();
    Context boundaryApplication = rootApplication ? Context.root() : APPLICATION;
    Context outerApplication = Context.root().with(KEY, "outer");
    Context boundaryOperation = Context.root().with(KEY, "boundary-operation");
    Scheduler scheduler = Schedulers.newSingle("context-boundary-test");
    AtomicReference<Subscription> subscription = new AtomicReference<>();
    AtomicReference<Context> boundaryCallbackContext = new AtomicReference<>();
    AtomicReference<Context> outerCallbackContext = new AtomicReference<>();
    CountDownLatch initialDrainFinished = new CountDownLatch(1);
    CountDownLatch boundaryCallbackFinished = new CountDownLatch(1);
    try {
      try (Scope ignored = UNRELATED.makeCurrent()) {
        Flux.from(runWithContext(Flux.just("boundary"), boundaryOperation, boundaryApplication))
            .publishOn(scheduler)
            .doOnNext(
                value -> {
                  boundaryCallbackContext.set(Context.current());
                  boundaryCallbackFinished.countDown();
                })
            .subscribe(noDemandSubscriber(subscription::set));
      }
      scheduler.schedule(initialDrainFinished::countDown);
      assertThat(initialDrainFinished.await(10, SECONDS)).isTrue();
      try (Scope ignored = UNRELATED.makeCurrent()) {
        Flux.from(runWithContext(Mono.just("outer"), OPERATION, outerApplication))
            .doOnNext(
                value -> {
                  assertContext(Context.current(), UNRELATED);
                  subscription.get().request(1);
                  outerCallbackContext.set(Context.current());
                })
            .blockLast();
        assertContext(Context.current(), UNRELATED);
      }
      assertThat(boundaryCallbackFinished.await(10, SECONDS)).isTrue();
      assertContext(boundaryCallbackContext.get(), UNRELATED);
      assertContext(outerCallbackContext.get(), UNRELATED);
    } finally {
      scheduler.dispose();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void temporaryOnSubscribeContextRestoresSubscriptionContext(boolean rootApplication)
      throws InterruptedException {
    assumeSchedulerHooksSupported(true);
    registerHooks();
    Context application = rootApplication ? Context.root() : APPLICATION;
    Context subscriptionContext = Context.root().with(KEY, "subscription");
    Context temporaryContext = Context.root().with(KEY, "temporary");
    Scheduler scheduler = Schedulers.newSingle("context-boundary-test");
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);
    CountDownLatch callbackFinished = new CountDownLatch(1);
    AtomicReference<Context> requestContext = new AtomicReference<>();
    AtomicReference<Context> contextAfterTemporaryScope = new AtomicReference<>();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    AtomicReference<Throwable> callbackError = new AtomicReference<>();
    try {
      scheduler.schedule(
          () -> {
            workerStarted.countDown();
            await(releaseWorker);
          });
      assertThat(workerStarted.await(10, SECONDS)).isTrue();
      try (Scope ignored = subscriptionContext.makeCurrent()) {
        Flux.from(runWithContext(Flux.just("value"), OPERATION, application))
            .publishOn(scheduler)
            .doOnNext(
                value -> {
                  callbackContext.set(Context.current());
                  callbackFinished.countDown();
                })
            .subscribe(
                new CoreSubscriber<String>() {
                  @Override
                  public reactor.util.context.Context currentContext() {
                    return reactor.util.context.Context.empty();
                  }

                  @Override
                  public void onSubscribe(Subscription subscription) {
                    try (Scope ignored = temporaryContext.makeCurrent()) {
                      subscription.request(1);
                      requestContext.set(Context.current());
                    }
                    contextAfterTemporaryScope.set(Context.current());
                  }

                  @Override
                  public void onNext(String value) {}

                  @Override
                  public void onError(Throwable error) {
                    callbackError.set(error);
                    callbackFinished.countDown();
                  }

                  @Override
                  public void onComplete() {}
                });
        assertContext(Context.current(), subscriptionContext);
      }
      releaseWorker.countDown();
      assertThat(callbackFinished.await(10, SECONDS)).isTrue();
      assertContext(requestContext.get(), temporaryContext);
      assertContext(contextAfterTemporaryScope.get(), subscriptionContext);
      assertThat(callbackError.get()).isNull();
      assertContext(callbackContext.get(), subscriptionContext);
    } finally {
      releaseWorker.countDown();
      scheduler.dispose();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void innerBoundariesRetainSharedSubscribersCapturedContext(boolean rootSecondApplication)
      throws InterruptedException {
    assumeSchedulerHooksSupported(true);
    registerHooks();
    Context subscriptionContext = Context.root().with(KEY, "subscription");
    Context secondApplication = rootSecondApplication ? Context.root() : INNER;
    Scheduler scheduler = Schedulers.newSingle("context-boundary-test");
    AtomicReference<MonoSink<String>> firstSink = new AtomicReference<>();
    AtomicReference<MonoSink<String>> secondSink = new AtomicReference<>();
    AtomicReference<Subscription> subscription = new AtomicReference<>();
    AtomicReference<Context> firstCallbackContext = new AtomicReference<>();
    AtomicReference<Context> secondCallbackContext = new AtomicReference<>();
    CountDownLatch initialDrainFinished = new CountDownLatch(1);
    CountDownLatch callbacksFinished = new CountDownLatch(2);
    try {
      try (Scope ignored = subscriptionContext.makeCurrent()) {
        Flux.just(0, 1)
            .flatMap(
                index ->
                    Flux.from(
                        runWithContext(
                            Mono.<String>create(
                                sink -> (index == 0 ? firstSink : secondSink).set(sink)),
                            OPERATION,
                            index == 0 ? APPLICATION : secondApplication)))
            .publishOn(scheduler)
            .doOnNext(
                value -> {
                  if (value.equals("first")) {
                    firstCallbackContext.set(Context.current());
                  } else {
                    secondCallbackContext.set(Context.current());
                  }
                  callbacksFinished.countDown();
                })
            .subscribe(noDemandSubscriber(subscription::set));
        assertContext(Context.current(), subscriptionContext);
      }

      assertThat(firstSink.get()).isNotNull();
      assertThat(secondSink.get()).isNotNull();
      firstSink.get().success("first");
      secondSink.get().success("second");
      scheduler.schedule(initialDrainFinished::countDown);
      assertThat(initialDrainFinished.await(10, SECONDS)).isTrue();

      try (Scope ignored = UNRELATED.makeCurrent()) {
        assertThat(subscription.get()).isNotNull();
        subscription.get().request(2);
        assertContext(Context.current(), UNRELATED);
      }
      assertThat(callbacksFinished.await(10, SECONDS)).isTrue();
      assertContext(firstCallbackContext.get(), subscriptionContext);
      assertContext(secondCallbackContext.get(), subscriptionContext);
    } finally {
      scheduler.dispose();
    }
  }

  @Test
  void transparentSharedSubscriberSeesEachSynchronousInnerContext() {
    registerHooks();
    AtomicReference<MonoSink<String>> firstSink = new AtomicReference<>();
    AtomicReference<MonoSink<String>> secondSink = new AtomicReference<>();
    AtomicReference<Context> firstCallbackContext = new AtomicReference<>();
    AtomicReference<Context> secondCallbackContext = new AtomicReference<>();
    try (Scope ignored = Context.root().makeCurrent()) {
      Flux.just(0, 1)
          .flatMap(
              index ->
                  Flux.from(
                      runWithContext(
                          Mono.<String>create(
                              sink -> (index == 0 ? firstSink : secondSink).set(sink)),
                          OPERATION,
                          index == 0 ? APPLICATION : INNER)))
          .doOnNext(
              value -> {
                if (value.equals("first")) {
                  firstCallbackContext.set(Context.current());
                } else {
                  secondCallbackContext.set(Context.current());
                }
              })
          .subscribe();

      assertThat(firstSink.get()).isNotNull();
      assertThat(secondSink.get()).isNotNull();
      firstSink.get().success("first");
      secondSink.get().success("second");
      assertContext(Context.current(), Context.root());
    }
    assertContext(firstCallbackContext.get(), APPLICATION);
    assertContext(secondCallbackContext.get(), INNER);
  }

  private static Publisher<String> source(boolean mono, String value) {
    return mono ? Mono.just(value) : Flux.just(value);
  }

  private static CoreSubscriber<String> noDemandSubscriber(Consumer<Subscription> onSubscribe) {
    return new CoreSubscriber<String>() {
      @Override
      public reactor.util.context.Context currentContext() {
        return reactor.util.context.Context.empty();
      }

      @Override
      public void onSubscribe(Subscription subscription) {
        onSubscribe.accept(subscription);
      }

      @Override
      public void onNext(String value) {}

      @Override
      public void onError(Throwable error) {
        throw new AssertionError(error);
      }

      @Override
      public void onComplete() {}
    };
  }

  private static void assertContext(Context actual, Context expected) {
    assertThat(actual.get(KEY)).isEqualTo(expected.get(KEY));
  }

  private static void assumeSchedulerHooksSupported(boolean required) {
    if (!required) {
      return;
    }
    try {
      Schedulers.class.getMethod("onScheduleHook", String.class, Function.class);
    } catch (NoSuchMethodException ignored) {
      assumeTrue(false);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
