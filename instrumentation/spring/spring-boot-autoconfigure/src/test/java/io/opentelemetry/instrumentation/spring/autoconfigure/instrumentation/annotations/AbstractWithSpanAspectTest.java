/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.annotations;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static io.opentelemetry.semconv.SemanticAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.SemanticAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
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
abstract class AbstractWithSpanAspectTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private WithSpanTester withSpanTester;
  private String unproxiedTesterSimpleClassName;
  private String unproxiedTesterClassName;

  public interface WithSpanTester {
    String testWithSpan();

    String testWithSpanWithValue();

    String testWithSpanWithException() throws Exception;

    String testWithClientSpan();

    CompletionStage<String> testAsyncCompletionStage(CompletionStage<String> stage);

    CompletableFuture<String> testAsyncCompletableFuture(CompletableFuture<String> stage);

    String withSpanAttributes(
        String discoveredName,
        String implicitName,
        String parameter,
        String nullAttribute,
        String notTraced);
  }

  abstract WithSpanTester newWithSpanTester();

  abstract WithSpanAspect newWithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer);

  @BeforeEach
  void setup() {
    WithSpanTester unproxiedTester = newWithSpanTester();
    unproxiedTesterSimpleClassName = unproxiedTester.getClass().getSimpleName();
    unproxiedTesterClassName = unproxiedTester.getClass().getName();

    AspectJProxyFactory factory = new AspectJProxyFactory();
    factory.setTarget(unproxiedTester);
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

    WithSpanAspect aspect = newWithSpanAspect(testing.getOpenTelemetry(), parameterNameDiscoverer);
    factory.addAspect(aspect);

    withSpanTester = factory.getProxy();
  }

  @Test
  @DisplayName("when method is annotated with @WithSpan should wrap method execution in a Span")
  void withSpanWithDefaults() {
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
                        span.hasName(unproxiedTesterSimpleClassName + ".testWithSpan")
                            .hasKind(INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                equalTo(CODE_FUNCTION, "testWithSpan"))));
  }

  @Test
  @DisplayName(
      "when @WithSpan value is set should wrap method execution in a Span with custom name")
  void withSpanName() {
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
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                equalTo(CODE_FUNCTION, "testWithSpanWithValue"))));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan AND an exception is thrown span should record the exception")
  void withSpanError() {
    assertThatThrownBy(() -> withSpanTester.testWithSpanWithException())
        .isInstanceOf(Exception.class);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(unproxiedTesterSimpleClassName + ".testWithSpanWithException")
                            .hasKind(INTERNAL)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                equalTo(CODE_FUNCTION, "testWithSpanWithException"))));
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan(kind=CLIENT) should build span with the declared SpanKind")
  void withSpanKind() {
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
                        span.hasName(unproxiedTesterSimpleClassName + ".testWithClientSpan")
                            .hasKind(CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                equalTo(CODE_FUNCTION, "testWithClientSpan"))));
  }

  @Test
  void withSpanAttributes() {
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
                        span.hasName(unproxiedTesterSimpleClassName + ".withSpanAttributes")
                            .hasKind(INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                equalTo(CODE_FUNCTION, "withSpanAttributes"),
                                equalTo(stringKey("discoveredName"), "foo"),
                                equalTo(stringKey("implicitName"), "bar"),
                                equalTo(stringKey("explicitName"), "baz"))));
  }

  @Nested
  @DisplayName("with a method annotated with @WithSpan returns CompletionStage")
  class WithCompletionStage {

    @Test
    @DisplayName("should end Span on complete")
    void onComplete() {
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
                          span.hasName(unproxiedTesterSimpleClassName + ".testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletionStage"))));
    }

    @Test
    @DisplayName("should end Span on completeException AND should record the exception")
    void onCompleteExceptionally() {
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
                          span.hasName(unproxiedTesterSimpleClassName + ".testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletionStage"))));
    }

    @Test
    @DisplayName("should end Span on incompatible return value")
    void onIncompatibleReturnValue() {
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
                          span.hasName(unproxiedTesterSimpleClassName + ".testAsyncCompletionStage")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletionStage"))));
    }
  }

  @Nested
  @DisplayName("with a method annotated with @WithSpan returns CompletableFuture")
  class WithCompletableFuture {

    @Test
    @DisplayName("should end Span on complete")
    void onComplete() {
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
                          span.hasName(
                                  unproxiedTesterSimpleClassName + ".testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletableFuture"))));
    }

    @Test
    @DisplayName("should end Span on completeException AND should record the exception")
    void onCompleteExceptionally() {
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
                          span.hasName(
                                  unproxiedTesterSimpleClassName + ".testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletableFuture"))));
    }

    @Test
    @DisplayName("should end the Span when already complete")
    void onCompletedFuture() {
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
                          span.hasName(
                                  unproxiedTesterSimpleClassName + ".testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletableFuture"))));
    }

    @Test
    @DisplayName("should end the Span when already failed")
    void onFailedFuture() {
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
                          span.hasName(
                                  unproxiedTesterSimpleClassName + ".testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasStatus(StatusData.error())
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletableFuture"))));
    }

    @Test
    @DisplayName("should end Span on incompatible return value")
    void onIncompatibleReturnValue() {
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
                          span.hasName(
                                  unproxiedTesterSimpleClassName + ".testAsyncCompletableFuture")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                                  equalTo(CODE_FUNCTION, "testAsyncCompletableFuture"))));
    }
  }
}
