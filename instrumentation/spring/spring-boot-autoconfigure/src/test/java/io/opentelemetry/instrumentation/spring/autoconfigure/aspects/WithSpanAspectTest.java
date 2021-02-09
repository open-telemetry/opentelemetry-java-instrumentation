/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.annotations.WithSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/** Spring AOP Test for {@link WithSpanAspect}. */
@ExtendWith(MockitoExtension.class)
public class WithSpanAspectTest {
  static class WithSpanTester {
    @WithSpan
    public String testWithSpan() {
      return "Span with name testWithSpan was created";
    }

    @WithSpan("greatestSpanEver")
    public String testWithSpanWithValue() {
      return "Span with name greatestSpanEver was created";
    }

    @WithSpan(kind = SpanKind.CLIENT)
    public String testWithSpanWithKind() {
      return "Span with name testWithSpanWithKind and SpanKind.CLIENT was created";
    }

    @WithSpan
    public String testWithSpanWithException() throws Exception {
      throw new Exception("Test @WithSpan With Exception");
    }
  }

  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private SpanBuilder spanBuilder;

  private WithSpanTester withSpanTester;

  @BeforeEach
  void setup() {
    // TODO(anuraaga): Replace mocking with a real tracer, this is more fragile than it needs be.
    when(tracer.spanBuilder(any())).thenReturn(spanBuilder);
    when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
    when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(span.storeInContext(any())).thenReturn(Context.root());

    AspectJProxyFactory factory = new AspectJProxyFactory(new WithSpanTester());
    WithSpanAspect aspect = new WithSpanAspect(tracer);
    factory.addAspect(aspect);

    withSpanTester = factory.getProxy();
  }

  @Test
  @DisplayName("when method is annotated with @WithSpan should wrap method execution in a Span")
  void withSpan() throws Throwable {

    withSpanTester.testWithSpan();

    verify(tracer, times(1)).spanBuilder("WithSpanTester.testWithSpan");
    verify(spanBuilder, times(1)).startSpan();
    verify(span, times(1)).end();
  }

  @Test
  @DisplayName(
      "when @WithSpan value is set should wrap method execution in a Span with custom name")
  void withSpanName() throws Throwable {

    withSpanTester.testWithSpanWithValue();

    verify(tracer, times(1)).spanBuilder("greatestSpanEver");
    verify(spanBuilder, times(1)).startSpan();
    verify(span, times(1)).end();
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan AND an exception is thrown span should record the exception")
  void withSpanError() throws Throwable {

    assertThatThrownBy(
            () -> {
              withSpanTester.testWithSpanWithException();
            })
        .isInstanceOf(Exception.class);

    verify(spanBuilder, times(1)).startSpan();
    verify(span, times(1)).recordException(any(Exception.class));
    verify(span, times(1)).end();
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan AND SpanKind is missing should set default SpanKind")
  void withSpanDefaultKind() throws Throwable {

    withSpanTester.testWithSpan();

    verify(spanBuilder, times(1)).setSpanKind(SpanKind.INTERNAL);
    verify(spanBuilder, times(1)).startSpan();
    verify(span, times(1)).end();
  }

  @Test
  @DisplayName(
      "when method is annotated with @WithSpan AND WithSpan.kind is set should build span with the declared SpanKind")
  void withSpanClientKind() throws Throwable {

    withSpanTester.testWithSpanWithKind();

    verify(spanBuilder, times(1)).setSpanKind(SpanKind.CLIENT);
    verify(spanBuilder, times(1)).startSpan();
    verify(span, times(1)).end();
  }
}
