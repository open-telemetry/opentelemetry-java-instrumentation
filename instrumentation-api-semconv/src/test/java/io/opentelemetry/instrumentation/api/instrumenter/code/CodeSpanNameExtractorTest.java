/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

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

    doReturn(TestClass.class).when(getter).getCodeClass(request);
    doReturn("doSomething").when(getter).getMethodName(request);

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

    doReturn(anon.getClass()).when(getter).getCodeClass(request);
    doReturn("doSomething").when(getter).getMethodName(request);

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

    doReturn(lambda.getClass()).when(getter).getCodeClass(request);
    doReturn("doSomething").when(getter).getMethodName(request);

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertEquals(getClass().getSimpleName() + "$$Lambda.doSomething", spanName);
  }

  static class TestClass {}

  static class AnonymousBaseClass {}
}
