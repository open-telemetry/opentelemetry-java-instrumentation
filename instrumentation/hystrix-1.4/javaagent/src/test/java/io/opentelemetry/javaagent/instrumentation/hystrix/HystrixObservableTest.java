/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Named.named;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
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

    class TestCommand extends HystrixObservableCommand<String> {
      protected TestCommand(Setter setter) {
        super(setter);
      }

      private String tracedMethod() {
        testing.runWithSpan("tracedMethod", () -> {});
        return "Hello!";
      }

      @Override
      protected Observable<String> construct() {
        Observable<String> obs = Observable.defer(() -> Observable.just(tracedMethod()).repeat(1));
        if (parameter.observeOn != null) {
          obs = obs.observeOn(parameter.observeOn);
        }
        if (parameter.subscribeOn != null) {
          obs = obs.subscribeOn(parameter.subscribeOn);
        }
        return obs;
      }
    }

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              HystrixObservableCommand<String> val = new TestCommand(setter("ExampleGroup"));
              return parameter.operation.apply(val);
            });

    assertThat(result).isEqualTo("Hello!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("ExampleGroup.TestCommand.execute")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
                            equalTo(stringKey("hystrix.group"), "ExampleGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span ->
                    span.hasName("tracedMethod")
                        .hasParent(trace.getSpan(1))
                        .hasAttributes(Attributes.empty())));
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

    class TestCommand extends HystrixObservableCommand<String> {
      protected TestCommand(Setter setter) {
        super(setter);
      }

      @Override
      protected Observable<String> construct() {
        Observable<String> err =
            Observable.defer(() -> Observable.error(new IllegalArgumentException()));
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
    }

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              HystrixObservableCommand<String> val = new TestCommand(setter("ExampleGroup"));
              return parameter.operation.apply(val);
            });

    assertThat(result).isEqualTo("Fallback!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("ExampleGroup.TestCommand.execute")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException()),
                span ->
                    span.hasName("ExampleGroup.TestCommand.fallback")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
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

    class TestCommand extends HystrixObservableCommand<String> {
      protected TestCommand(Setter setter) {
        super(setter);
      }

      @Override
      protected Observable<String> construct() {
        Observable<String> err =
            Observable.defer(() -> Observable.error(new IllegalArgumentException()));
        if (parameter.observeOn != null) {
          err = err.observeOn(parameter.observeOn);
        }
        if (parameter.subscribeOn != null) {
          err = err.subscribeOn(parameter.subscribeOn);
        }
        return err;
      }
    }

    Throwable exception =
        catchException(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      HystrixObservableCommand<String> val =
                          new TestCommand(setter("FailingGroup"));
                      return parameter.operation.apply(val);
                    }));

    assertThat(exception)
        .isInstanceOf(HystrixRuntimeException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    HystrixRuntimeException hystrixRuntimeException = (HystrixRuntimeException) exception;

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(exception),
                span ->
                    span.hasName("FailingGroup.TestCommand.execute")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(exception.getCause())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
                            equalTo(stringKey("hystrix.group"), "FailingGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span ->
                    span.hasName("FailingGroup.TestCommand.fallback")
                        .hasParent(trace.getSpan(1))
                        .hasException(hystrixRuntimeException.getFallbackException())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
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
