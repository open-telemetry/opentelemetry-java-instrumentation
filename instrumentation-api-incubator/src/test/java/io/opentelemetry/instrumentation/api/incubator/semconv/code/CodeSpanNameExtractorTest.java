/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeSpanNameExtractorTest {
  @Mock CodeAttributesGetter<Object> getter;

  @Test
  void shouldExtractFullSpanName() {
    // given
    Object request = new Object();

    when(getter.getCodeClass(request)).thenAnswer(invocation -> TestClass.class);
    when(getter.getMethodName(request)).thenReturn("doSomething");

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertThat(spanName).isEqualTo("TestClass.doSomething");
  }

  @Test
  void shouldExtractFullSpanNameForAnonymousClass() {
    // given
    AnonymousBaseClass anon = new AnonymousBaseClass() {};
    Object request = new Object();

    when(getter.getCodeClass(request)).thenAnswer(invocation -> anon.getClass());
    when(getter.getMethodName(request)).thenReturn("doSomething");

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertThat(spanName).isEqualTo(getClass().getSimpleName() + "$1.doSomething");
  }

  @Test
  void shouldExtractFullSpanNameForLambda() {
    // given
    Runnable lambda = () -> {};
    Object request = new Object();

    when(getter.getCodeClass(request)).thenAnswer(invocation -> lambda.getClass());
    when(getter.getMethodName(request)).thenReturn("doSomething");

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertThat(spanName).isEqualTo(getClass().getSimpleName() + "$$Lambda.doSomething");
  }

  static class TestClass {}

  static class AnonymousBaseClass {}
}
