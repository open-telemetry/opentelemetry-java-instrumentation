/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import rx.Observable;
import rx.schedulers.Schedulers;

class HystrixObservableChainTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("RxReturnValueIgnored")
  void testCommand() {

    class TestCommand extends HystrixObservableCommand<String> {
      TestCommand(Setter setter) {
        super(setter);
      }

      private String tracedMethod() {
        testing.runWithSpan("tracedMethod", () -> {});
        return "Hello";
      }

      @Override
      protected Observable<String> construct() {
        return Observable.defer(() -> Observable.just(tracedMethod()))
            .subscribeOn(Schedulers.immediate());
      }
    }

    class AnotherTestCommand extends HystrixObservableCommand<String> {
      private final String str;

      AnotherTestCommand(Setter setter, String str) {
        super(setter);
        this.str = str;
      }

      private String anotherTracedMethod() {
        testing.runWithSpan("anotherTracedMethod", () -> {});
        return str + "!";
      }

      @Override
      protected Observable<String> construct() {
        return Observable.defer(() -> Observable.just(anotherTracedMethod()))
            .subscribeOn(Schedulers.computation());
      }
    }

    String result =
        testing.runWithSpan(
            "parent",
            () ->
                new TestCommand(setter("ExampleGroup"))
                    .toObservable()
                    .subscribeOn(Schedulers.io())
                    .map(String::toUpperCase)
                    .flatMap(
                        str ->
                            new AnotherTestCommand(setter("OtherGroup"), str)
                                .toObservable()
                                .subscribeOn(Schedulers.trampoline()))
                    .toBlocking()
                    .first());

    assertThat(result).isEqualTo("HELLO!");

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
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("OtherGroup.AnotherTestCommand.execute")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "AnotherTestCommand"),
                            equalTo(stringKey("hystrix.group"), "OtherGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span ->
                    span.hasName("anotherTracedMethod")
                        .hasParent(trace.getSpan(3))
                        .hasAttributes(Attributes.empty())));
  }

  private static HystrixObservableCommand.Setter setter(String key) {
    HystrixObservableCommand.Setter setter =
        HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(key));
    setter.andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(10_000));
    return setter;
  }
}
