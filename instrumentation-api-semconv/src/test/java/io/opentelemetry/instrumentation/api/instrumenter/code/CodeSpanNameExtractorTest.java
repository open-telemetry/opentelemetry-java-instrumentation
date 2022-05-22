/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.willReturn;

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

    willReturn(TestClass.class).given(getter).codeClass(request);
    willReturn("doSomething").given(getter).methodName(request);

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

    willReturn(anon.getClass()).given(getter).codeClass(request);
    willReturn("doSomething").given(getter).methodName(request);

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertEquals(getClass().getSimpleName() + "$1.doSomething", spanName);
  }

  static class TestClass {}

  static class AnonymousBaseClass {}
}
