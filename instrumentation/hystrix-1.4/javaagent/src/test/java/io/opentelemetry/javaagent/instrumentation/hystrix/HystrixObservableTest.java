/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
  @SuppressWarnings("RxReturnValueIgnored")
  void testCommands(Parameter parameter) {

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              HystrixObservableCommand<String> val =
                  new HystrixObservableCommand<String>(setter()) {
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

  private static Stream<Arguments> provideCommandActionArguments() {
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
                    null, Schedulers.newThread(), cmd -> cmd.observe().toBlocking().first()))),
        Arguments.of(
            named(
                "toObservable block",
                new Parameter(
                    Schedulers.computation(),
                    Schedulers.newThread(),
                    cmd -> {
                      BlockingQueue<String> queue = new LinkedBlockingQueue<>();
                      Subscription subscription =
                          cmd.observe()
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
                    }))));
  }

  private static HystrixObservableCommand.Setter setter() {
    HystrixObservableCommand.Setter setter =
        HystrixObservableCommand.Setter.withGroupKey(
            HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
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
