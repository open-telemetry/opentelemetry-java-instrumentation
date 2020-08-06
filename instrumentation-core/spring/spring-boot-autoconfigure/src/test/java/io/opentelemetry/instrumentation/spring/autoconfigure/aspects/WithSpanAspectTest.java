/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Scope;
import io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

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

    @WithSpan
    public String testWithSpanWithException() throws Exception {
      throw new Exception("Test @WithSpan With Exception");
    }
  }

  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Span.Builder spanBuilder;
  @Mock private Scope scope;
  @Mock private ProceedingJoinPoint pjp;
  @Mock private MethodSignature signature;

  private WithSpanTester withSpanTester;

  @BeforeEach
  void setup() {
    when(tracer.spanBuilder(any())).thenReturn(spanBuilder);
    when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(scope);

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
}
