/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.SpanAttribute;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.ParameterNameDiscoverer;

/** Spring AOP Test for {@link WithSpanAspect}. */
public class WithSpanAspectTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static class WithSpanTester {
    @WithSpan
    public String testWithSpan() {
      return "Span with name testWithSpan was created";
    }

    @WithSpan("greatestSpanEver")
    public String testWithSpanWithValue() {
      return "Span with name greatestSpanEver was created";
    }

    @WithSpan
    public String testWithSpanWithException() throws Exception {
      throw new Exception("Test @WithSpan With Exception");
    }

    @WithSpan(kind = CLIENT)
    public String testWithClientSpan() {
      return "Span with name testWithClientSpan and SpanKind.CLIENT was created";
    }

    @WithSpan(kind = SpanKind.SERVER)
    public String testWithServerSpan() {
      return "Span with name testWithServerSpan and SpanKind.SERVER was created";
    }

    @WithSpan
    public CompletionStage<String> testAsyncCompletionStage(CompletionStage<String> stage) {
      return stage;
    }

    @WithSpan
    public CompletableFuture<String> testAsyncCompletableFuture(CompletableFuture<String> stage) {
      return stage;
    }

    @WithSpan
    public String withSpanAttributes(
        @SpanAttribute String discoveredName,
        @SpanAttribute String implicitName,
        @SpanAttribute("explicitName") String parameter,
        @SpanAttribute("nullAttribute") String nullAttribute,
        String notTraced) {

      return "hello!";
    }
  }

  private WithSpanTester withSpanTester;

  @BeforeEach
  void setup() {
    AspectJProxyFactory factory = new AspectJProxyFactory(new WithSpanTester());
    ParameterNameDiscoverer parameterNameDiscoverer =
        new ParameterNameDiscoverer() {
          @Override
          public String[] getParameterNames(Method method) {
            return new String[] {"discoveredName", null, "parameter", "nullAttribute", "notTraced"};
          }

          @Override
          public String[] getParameterNames(Constructor<?> constructor) {
            return null;
          }
        };

    WithSpanAspect aspect = new WithSpanAspect(testing.getOpenTelemetry(), parameterNameDiscoverer);
    factory.addAspect(aspect);

    withSpanTester = factory.getProxy();
  }

  @Test
  @DisplayName("when method is annotated with @WithSpan should wrap method execution in a Span")
  void withSpanWithDefaults() throws Throwable {
    // when
    testing.runWithSpan("parent", withSpanTester::testWithSpan);

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                    span ->
                        span.hasName("WithSpanTester.testWithSpan")
                            .hasKind(INTERNAL)
                            // otel SDK assertions need some work before we can comfortably use
                            // them in this project...
                            .hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Test
  @DisplayName(
      "when @WithSpan value is set should wrap method execution in a Span with custom name")
  void withSpanName() throws Throwable {
    // when
    testing.runWithSpan("parent", () -> withSpanTester.testWithSpanWithValue());

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                    span ->
                        span.hasName("greatestSpanEver")
                            .hasKind(INTERNAL)
                            .hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan AND an exception is thrown span should record the exception")
  void withSpanError() throws Throwable {
    assertThatThrownBy(() -> withSpanTester.testWithSpanWithException())
        .isInstanceOf(Exception.class);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("WithSpanTester.testWithSpanWithException")
                            .hasKind(INTERNAL)
                            .hasStatus(StatusData.error())));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan(kind=CLIENT) should build span with the declared SpanKind")
  void withSpanKind() throws Throwable {
    // when
    testing.runWithSpan("parent", () -> withSpanTester.testWithClientSpan());

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                    span ->
                        span.hasName("WithSpanTester.testWithClientSpan")
                            .hasKind(CLIENT)
                            .hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan(kind=CLIENT) and context already contains a CLIENT span should suppress span")
  void suppressClientSpan() throws Throwable {
    // when
    testing.runWithHttpClientSpan("parent", withSpanTester::testWithClientSpan);

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(CLIENT)));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan(kind=SERVER) and context already contains a SERVER span should suppress span")
  void suppressServerSpan() throws Throwable {
    // when
    testing.runWithHttpServerSpan("parent", withSpanTester::testWithServerSpan);

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(SERVER)));
  }

  @Test
  @DisplayName("")
  void withSpanAttributes() throws Throwable {
    // when
    testing.runWithSpan(
        "parent", () -> withSpanTester.withSpanAttributes("foo", "bar", "baz", null, "fizz"));

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                    span ->
                        span.hasName("WithSpanTester.withSpanAttributes")
                            .hasKind(INTERNAL)
                            .hasAttributes(
                                Attributes.of(
                                    AttributeKey.stringKey("discoveredName"), "foo",
                                    AttributeKey.stringKey("implicitName"), "bar",
                                    AttributeKey.stringKey("explicitName"), "baz"))
                            .hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Nested
  @DisplayName("with a method annotated with @WithSpan returns CompletionStage")
  class WithCompletionStage {

    @Test
    @DisplayName("should end Span on complete")
    void onComplete() throws Throwable {
      CompletableFuture<String> future = new CompletableFuture<>();

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletionStage(future));

      // then
      assertThat(testing.waitForTraces(1))
          .hasTracesSatisfyingExactly(
              trace ->
                  trace
                      .hasSize(1)
                      .hasSpansSatisfyingExactly(span -> span.hasName("parent").hasKind(INTERNAL)));

      // when
      future.complete("DONE");

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end Span on completeException AND should record the exception")
    void onCompleteExceptionally() throws Throwable {
      CompletableFuture<String> future = new CompletableFuture<>();

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletionStage(future));

      // then
      assertThat(testing.waitForTraces(1))
          .hasTracesSatisfyingExactly(
              trace ->
                  trace
                      .hasSize(1)
                      .hasSpansSatisfyingExactly(span -> span.hasName("parent").hasKind(INTERNAL)));

      // when
      future.completeExceptionally(new Exception("Test @WithSpan With completeExceptionally"));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end Span on incompatible return value")
    void onIncompatibleReturnValue() throws Throwable {
      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletionStage(null));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }
  }

  @Nested
  @DisplayName("with a method annotated with @WithSpan returns CompletableFuture")
  class WithCompletableFuture {

    @Test
    @DisplayName("should end Span on complete")
    void onComplete() throws Throwable {
      CompletableFuture<String> future = new CompletableFuture<>();

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletableFuture(future));

      // then
      assertThat(testing.waitForTraces(1))
          .hasTracesSatisfyingExactly(
              trace ->
                  trace
                      .hasSize(1)
                      .hasSpansSatisfyingExactly(span -> span.hasName("parent").hasKind(INTERNAL)));

      // when
      future.complete("DONE");

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end Span on completeException AND should record the exception")
    void onCompleteExceptionally() throws Throwable {
      CompletableFuture<String> future = new CompletableFuture<>();

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletableFuture(future));

      // then
      assertThat(testing.waitForTraces(1))
          .hasTracesSatisfyingExactly(
              trace ->
                  trace
                      .hasSize(1)
                      .hasSpansSatisfyingExactly(span -> span.hasName("parent").hasKind(INTERNAL)));

      // when
      future.completeExceptionally(new Exception("Test @WithSpan With completeExceptionally"));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end the Span when already complete")
    void onCompletedFuture() throws Throwable {
      CompletableFuture<String> future = CompletableFuture.completedFuture("Done");

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletableFuture(future));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end the Span when already failed")
    void onFailedFuture() throws Throwable {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.completeExceptionally(new Exception("Test @WithSpan With completeExceptionally"));

      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletableFuture(future));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }

    @Test
    @DisplayName("should end Span on incompatible return value")
    void onIncompatibleReturnValue() throws Throwable {
      // when
      testing.runWithSpan("parent", () -> withSpanTester.testAsyncCompletableFuture(null));

      // then
      List<List<SpanData>> traces = testing.waitForTraces(1);
      assertThat(traces)
          .hasTracesSatisfyingExactly(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      parentSpan -> parentSpan.hasName("parent").hasKind(INTERNAL),
                      span ->
                          span.hasName("WithSpanTester.testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParentSpanId(traces.get(0).get(0).getSpanId())));
    }
  }
}
