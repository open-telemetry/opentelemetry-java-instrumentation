/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.MemberAttributeExtension;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class WithSpanInstrumentationTest {

  @RegisterExtension
  public static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void deriveAutomaticName() throws Exception {

    new TracedWithSpan().otel();

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.otel")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "otel")))));
  }

  @Test
  void manualName() throws Exception {

    new TracedWithSpan().namedOtel();

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("manualName")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "namedOtel")))));
  }

  @Test
  void manualKind() throws Exception {

    new TracedWithSpan().someKind();

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.someKind")
                                .hasKind(SpanKind.PRODUCER)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "someKind")))));
  }

  @Test
  void multipleSpans() throws Exception {

    new TracedWithSpan().server();

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.server")
                                .hasKind(SpanKind.SERVER)
                                .hasNoParent()
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "server"))),
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.otel")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParent(trace.get(0))
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "otel")))));
  }

  @Test
  void excludedMethod() throws Exception {

    new TracedWithSpan().ignored();

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void completedCompletionStage() throws Exception {

    CompletableFuture<String> future = CompletableFuture.completedFuture("Done");
    new TracedWithSpan().completionStage(future);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completionStage")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completionStage")))));
  }

  @Test
  void exceptionallyCompletedCompletionStage() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    future.completeExceptionally(new IllegalArgumentException("Boom"));
    new TracedWithSpan().completionStage(future);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completionStage")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasStatus(StatusData.error())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completionStage")))));
  }

  @Test
  void nullCompletionStage() throws Exception {

    new TracedWithSpan().completionStage(null);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completionStage")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completionStage")))));
  }

  @Test
  void completingCompletionStage() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completionStage(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.complete("Done");

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completionStage")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completionStage")))));
  }

  @Test
  void exceptionallyCompletingCompletionStage() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completionStage(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.completeExceptionally(new IllegalArgumentException("Boom"));

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completionStage")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasStatus(StatusData.error())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completionStage")))));
  }

  @Test
  void completedCompletableFuture() throws Exception {

    CompletableFuture<String> future = CompletableFuture.completedFuture("Done");
    new TracedWithSpan().completableFuture(future);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completableFuture")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completableFuture")))));
  }

  @Test
  void exceptionallyCompletedCompletableFuture() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    future.completeExceptionally(new IllegalArgumentException("Boom"));
    new TracedWithSpan().completableFuture(future);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completableFuture")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasStatus(StatusData.error())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completableFuture")))));
  }

  @Test
  void nullCompletableFuture() throws Exception {

    new TracedWithSpan().completableFuture(null);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completableFuture")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completableFuture")))));
  }

  @Test
  void completingCompletableFuture() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completableFuture(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.complete("Done");

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completableFuture")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completableFuture")))));
  }

  @Test
  void exceptionallyCompletingCompletableFuture() throws Exception {

    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completableFuture(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.completeExceptionally(new IllegalArgumentException("Boom"));

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.completableFuture")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasStatus(StatusData.error())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "completableFuture")))));
  }

  @Test
  void captureAttributes() throws Exception {

    new TracedWithSpan().withSpanAttributes("foo", "bar", null, "baz");

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("TracedWithSpan.withSpanAttributes")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    TracedWithSpan.class.getName()),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "withSpanAttributes"),
                                                entry(
                                                    AttributeKey.stringKey("implicitName"), "foo"),
                                                entry(
                                                    AttributeKey.stringKey("explicitName"),
                                                    "bar")))));
  }

  // Needs to be public for ByteBuddy
  public static class Intercept {
    @RuntimeType
    public void intercept(@This Object o) {
      testing.runWithSpan("intercept", () -> {});
    }
  }

  @Test
  void java6Class() throws Exception {
    /*
    class GeneratedJava6TestClass implements Runnable {
      @WithSpan
      public void run() {
        testing.runWithSpan("intercept", () -> {});
      }
    }
    */
    Class<?> generatedClass =
        new ByteBuddy(ClassFileVersion.JAVA_V6)
            .subclass(Object.class)
            .name("GeneratedJava6TestClass")
            .implement(Runnable.class)
            .defineMethod("run", void.class, Modifier.PUBLIC)
            .intercept(MethodDelegation.to(new Intercept()))
            .visit(
                new MemberAttributeExtension.ForMethod()
                    .annotateMethod(
                        AnnotationDescription.Builder.ofType(
                                io.opentelemetry.extension.annotations.WithSpan.class)
                            .build())
                    .on(ElementMatchers.named("run")))
            .make()
            .load(getClass().getClassLoader())
            .getLoaded();

    Runnable runnable = (Runnable) generatedClass.getConstructor().newInstance();
    runnable.run();

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("GeneratedJava6TestClass.run")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                                    "GeneratedJava6TestClass"),
                                                entry(
                                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                                    "run"))),
                        span ->
                            assertThat(span)
                                .hasName("intercept")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(trace.get(0).getSpanId())
                                .hasAttributesSatisfying(
                                    attributes -> assertThat(attributes).isEmpty())));
  }
}
