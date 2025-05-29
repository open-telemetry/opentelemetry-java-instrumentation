/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertEquals("TestClass.doSomething", spanName);
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
    assertEquals(getClass().getSimpleName() + "$1.doSomething", spanName);
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
    assertEquals(getClass().getSimpleName() + "$$Lambda.doSomething", spanName);
  }

  static class TestClass {}

  static class AnonymousBaseClass {}
}
