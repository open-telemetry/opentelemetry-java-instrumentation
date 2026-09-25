/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

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
  void extractsFullSpanName() {
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> TestClass.class);
    when(getter.getMethodName(request)).thenReturn("doSomething");

    SpanNameExtractor<Object> extractor = CodeSpanNameExtractor.create(getter);

    assertThat(extractor.extract(request)).isEqualTo("TestClass.doSomething");
  }

  @Test
  void extractsAnonymousClassName() {
    AnonymousBaseClass anon = new AnonymousBaseClass() {};
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> anon.getClass());
    when(getter.getMethodName(request)).thenReturn("doSomething");

    assertThat(CodeSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(getClass().getSimpleName() + "$1.doSomething");
  }

  @Test
  void extractsLambdaClassName() {
    Runnable lambda = () -> {};
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> lambda.getClass());
    when(getter.getMethodName(request)).thenReturn("doSomething");

    assertThat(CodeSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(getClass().getSimpleName() + "$$Lambda.doSomething");
  }

  @Test
  void handlesMissingClassAndMethod() {
    Object request = new Object();

    assertThat(CodeSpanNameExtractor.create(getter).extract(request)).isEqualTo("<unknown>");
  }

  static class TestClass {}

  static class AnonymousBaseClass {}
}
