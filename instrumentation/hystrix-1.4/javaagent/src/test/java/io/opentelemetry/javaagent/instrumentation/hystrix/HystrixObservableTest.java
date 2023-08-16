/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Named.named;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;

class HystrixObservableTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("provideCommandActionArguments")
  void testCommands(Parameter parameter) {

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              HystrixObservableCommand<String> val =
                  new HystrixObservableCommand<String>(setter("ExampleGroup")) {
                    private String tracedMethod() {
                      testing.runWithSpan("tracedMethod", () -> {});
                      return "Hello!";
                    }

                    @Override
                    protected Observable<String> construct() {
                      Observable<String> obs =
                          Observable.defer(() -> Observable.just(tracedMethod()).repeat(1));
                      if (parameter.observeOn != null) {
                        obs = obs.observeOn(parameter.observeOn);
                      }
                      if (parameter.subscribeOn != null) {
                        obs = obs.subscribeOn(parameter.subscribeOn);
                      }
                      return obs;
                    }
                  };
              return parameter.operation.apply(val);
            });

    assertThat(Objects.equals(result, "Hello!")).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("ExampleGroup.HystrixObservableTest$1.execute")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "HystrixObservableTest$1"),
                            equalTo(stringKey("hystrix.group"), "ExampleGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span -> span.hasName("tracedMethod").hasParent(trace.getSpan(1))));
  }

  private static Stream<Arguments> baseArguments() {
    return Stream.of(
        Arguments.of(
            named(
                "toObservable",
                new Parameter(null, null, cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable-I",
                new Parameter(
                    Schedulers.immediate(), null, cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable-T",
                new Parameter(
                    Schedulers.trampoline(),
                    null,
                    cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable-C",
                new Parameter(
                    Schedulers.computation(),
                    null,
                    cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable-IO",
                new Parameter(
                    Schedulers.io(), null, cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable-NT",
                new Parameter(
                    Schedulers.newThread(), null, cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable+I",
                new Parameter(
                    null, Schedulers.immediate(), cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable+T",
                new Parameter(
                    null,
                    Schedulers.trampoline(),
                    cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable+C",
                new Parameter(
                    null,
                    Schedulers.computation(),
                    cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable+IO",
                new Parameter(
                    null, Schedulers.io(), cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable+NT",
                new Parameter(
                    null, Schedulers.newThread(), cmd -> cmd.toObservable().toBlocking().first()))),
        Arguments.of(
            named("observe", new Parameter(null, null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe-I",
                new Parameter(
                    Schedulers.immediate(), null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe-T",
                new Parameter(
                    Schedulers.trampoline(), null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe-C",
                new Parameter(
                    Schedulers.computation(), null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe-IO",
                new Parameter(Schedulers.io(), null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe-NT",
                new Parameter(
                    Schedulers.newThread(), null, cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe+I",
                new Parameter(
                    null, Schedulers.immediate(), cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe+T",
                new Parameter(
                    null, Schedulers.trampoline(), cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe+C",
                new Parameter(
                    null, Schedulers.computation(), cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe+IO",
                new Parameter(null, Schedulers.io(), cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "observe+NT",
                new Parameter(
                    null, Schedulers.newThread(), cmd -> cmd.observe().toBlocking().first()))));
  }

  private static Stream<Arguments> provideCommandActionArguments() {
    return Stream.concat(
        baseArguments(),
        Stream.of(
            Arguments.of(
                named(
                    "toObservable block",
                    new Parameter(
                        Schedulers.computation(),
                        Schedulers.newThread(),
                        cmd -> {
                          BlockingQueue<String> queue = new LinkedBlockingQueue<>();
                          Subscription subscription =
                              cmd.toObservable()
                                  .subscribe(
                                      next -> {
                                        try {
                                          queue.put(next);
                                        } catch (InterruptedException e) {
                                          throw new RuntimeException(e);
                                        }
                                      });
                          String returnValue;
                          try {
                            returnValue = queue.take();

                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                          subscription.unsubscribe();
                          return returnValue;
                        })))));
  }

  @ParameterizedTest
  @MethodSource("provideCommandFallbackArguments")
  void testCommandFallbacks(Parameter parameter) {

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              HystrixObservableCommand<String> val =
                  new HystrixObservableCommand<String>(setter("ExampleGroup")) {
                    @Override
                    protected Observable<String> construct() {
                      Observable<String> err =
                          Observable.defer(() -> Observable.error(new IllegalArgumentException()))
                              .map(
                                  error -> {
                                    if (error instanceof Throwable) {
                                      return ((Throwable) error).getMessage();
                                    } else {
                                      throw new IllegalStateException("Expected Throwable result");
                                    }
                                  })
                              .repeat(1);
                      if (parameter.observeOn != null) {
                        err = err.observeOn(parameter.observeOn);
                      }
                      if (parameter.subscribeOn != null) {
                        err = err.subscribeOn(parameter.subscribeOn);
                      }
                      return err;
                    }

                    @Override
                    protected Observable<String> resumeWithFallback() {
                      return Observable.just("Fallback!").repeat(1);
                    }
                  };
              return parameter.operation.apply(val);
            });

    assertThat(Objects.equals(result, "Fallback!")).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("ExampleGroup.HystrixObservableTest$2.execute")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, "java.lang.IllegalArgumentException"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)))),
                span ->
                    span.hasName("ExampleGroup.HystrixObservableTest$2.fallback")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "HystrixObservableTest$2"),
                            equalTo(stringKey("hystrix.group"), "ExampleGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false))));
  }

  private static Stream<Arguments> provideCommandFallbackArguments() {
    return Stream.concat(
        baseArguments(),
        Stream.of(
            Arguments.of(
                named(
                    "toObservable block",
                    new Parameter(
                        null,
                        null,
                        cmd -> {
                          BlockingQueue<String> queue = new LinkedBlockingQueue<>();
                          Subscription subscription =
                              cmd.toObservable()
                                  .subscribe(
                                      next -> {
                                        try {
                                          queue.put(next);
                                        } catch (InterruptedException e) {
                                          throw new RuntimeException(e);
                                        }
                                      });
                          String returnValue;
                          try {
                            returnValue = queue.take();
                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                          subscription.unsubscribe();
                          return returnValue;
                        })))));
  }

  @ParameterizedTest
  @MethodSource("provideCommandNoFallbackResultsInErrorArguments")
  void testNoFallbackResultsInErrorForAction(Parameter parameter) {

    Throwable exception =
        catchException(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      HystrixObservableCommand<String> val =
                          new HystrixObservableCommand<String>(setter("FailingGroup")) {
                            @Override
                            protected Observable<String> construct() {
                              Observable<String> err =
                                  Observable.defer(
                                          () -> Observable.error(new IllegalArgumentException()))
                                      .map(
                                          error -> {
                                            if (error instanceof Throwable) {
                                              return ((Throwable) error).getMessage();
                                            } else {
                                              throw new IllegalStateException(
                                                  "Expected Throwable result");
                                            }
                                          })
                                      .repeat(1);
                              if (parameter.observeOn != null) {
                                err = err.observeOn(parameter.observeOn);
                              }
                              if (parameter.subscribeOn != null) {
                                err = err.subscribeOn(parameter.subscribeOn);
                              }
                              return err;
                            }
                          };
                      return parameter.operation.apply(val);
                    }));

    assertThat(exception).hasCauseInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)),
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            "com.netflix.hystrix.exception.HystrixRuntimeException"),
                                        equalTo(
                                            EXCEPTION_MESSAGE,
                                            "HystrixObservableTest$3 failed and no fallback available."))),
                span ->
                    span.hasName("FailingGroup.HystrixObservableTest$3.execute")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, "java.lang.IllegalArgumentException"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "HystrixObservableTest$3"),
                            equalTo(stringKey("hystrix.group"), "FailingGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span ->
                    span.hasName("FailingGroup.HystrixObservableTest$3.fallback")
                        .hasParent(trace.getSpan(1))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            "java.lang.UnsupportedOperationException"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)),
                                        equalTo(EXCEPTION_MESSAGE, "No fallback available.")))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "HystrixObservableTest$3"),
                            equalTo(stringKey("hystrix.group"), "FailingGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false))));
  }

  private static Stream<Arguments> provideCommandNoFallbackResultsInErrorArguments() {
    return Stream.concat(
        baseArguments(),
        Stream.of(
            Arguments.of(
                named(
                    "toObservable block",
                    new Parameter(
                        Schedulers.computation(),
                        Schedulers.newThread(),
                        cmd -> {
                          BlockingQueue<Throwable> queue = new LinkedBlockingQueue<>();
                          Subscription subscription =
                              cmd.toObservable()
                                  .subscribe(
                                      next -> {
                                        try {
                                          queue.put(new Exception("Unexpectedly got a next"));
                                        } catch (InterruptedException e) {
                                          throw new IllegalArgumentException(e);
                                        }
                                      },
                                      next -> {
                                        try {
                                          queue.put(next);
                                        } catch (InterruptedException e) {
                                          throw new RuntimeException(e);
                                        }
                                      });
                          Throwable returnValue;
                          try {
                            returnValue = queue.take();

                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                          subscription.unsubscribe();
                          try {
                            throw returnValue;
                          } catch (Throwable e) {
                            throw (HystrixRuntimeException) e;
                          }
                        })))));
  }

  private static HystrixObservableCommand.Setter setter(String key) {
    HystrixObservableCommand.Setter setter =
        HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(key));
    setter.andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(10_000));
    return setter;
  }

  private static class Parameter {
    public final Scheduler observeOn;
    public final Scheduler subscribeOn;
    public final Function<HystrixObservableCommand<String>, String> operation;

    public Parameter(
        Scheduler observeOn,
        Scheduler subscribeOn,
        Function<HystrixObservableCommand<String>, String> operation) {
      this.observeOn = observeOn;
      this.subscribeOn = subscribeOn;
      this.operation = operation;
    }
  }
}
