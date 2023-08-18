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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HystrixTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("provideCommandActionArguments")
  void testCommands(Function<HystrixCommand<String>, String> operation) {
    class TestCommand extends HystrixCommand<String> {
      protected TestCommand(Setter setter) {
        super(setter);
      }

      @Override
      protected String run() throws Exception {
        return tracedMethod();
      }

      private String tracedMethod() {
        testing.runWithSpan("tracedMethod", () -> {});
        return "Hello!";
      }
    }

    HystrixCommand<String> command = new TestCommand(setter());

    String result = testing.runWithSpan("parent", () -> operation.apply(command));
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

  @ParameterizedTest
  @MethodSource("provideCommandActionArguments")
  void testCommandFallbacks(Function<HystrixCommand<String>, String> operation) {
    class TestCommand extends HystrixCommand<String> {
      protected TestCommand(Setter setter) {
        super(setter);
      }

      @Override
      protected String run() throws Exception {
        throw new IllegalArgumentException();
      }

      @Override
      protected String getFallback() {
        return "Fallback!";
      }
    }

    HystrixCommand<String> command = new TestCommand(setter());

    String result = testing.runWithSpan("parent", () -> operation.apply(command));
    assertThat(result).isEqualTo("Fallback!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("ExampleGroup.TestCommand.execute")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
                            equalTo(stringKey("hystrix.group"), "ExampleGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false)),
                span ->
                    span.hasName("ExampleGroup.TestCommand.fallback")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("hystrix.command"), "TestCommand"),
                            equalTo(stringKey("hystrix.group"), "ExampleGroup"),
                            equalTo(booleanKey("hystrix.circuit_open"), false))));
  }

  private static Stream<Arguments> provideCommandActionArguments() {
    return Stream.of(
        Arguments.of(
            named("execute", (Function<HystrixCommand<String>, String>) HystrixCommand::execute)),
        Arguments.of(
            named(
                "queue",
                (Function<HystrixCommand<String>, String>)
                    cmd -> {
                      try {
                        return cmd.queue().get();
                      } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                      }
                    })),
        Arguments.of(
            named(
                "toObservable",
                (Function<HystrixCommand<String>, String>)
                    cmd -> cmd.toObservable().toBlocking().first())),
        Arguments.of(
            named(
                "observe",
                (Function<HystrixCommand<String>, String>)
                    cmd -> cmd.observe().toBlocking().first())),
        Arguments.of(
            named(
                "observe block",
                (Function<HystrixCommand<String>, String>)
                    cmd -> {
                      BlockingQueue<String> queue = new LinkedBlockingQueue<>();
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
                      return returnValue;
                    })));
  }

  private static HystrixCommand.Setter setter() {
    HystrixCommand.Setter setter =
        HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
    setter.andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(10_000));
    return setter;
  }
}
