/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("checkstyle:TypeName") // testing generated class names
class CodeSpanNameExtractorTest {
  @Mock CodeAttributesGetter<Object> getter;

  @Test
  void shouldExtractFullSpanName() {
    // given
    Object request = new Object();

    when(getter.getCodeClass(request)).thenAnswer(invocation -> TestClass.class);
    when(getter.getCodeMethodName(request)).thenReturn("doSomething");

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
    when(getter.getCodeMethodName(request)).thenReturn("doSomething");

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
    when(getter.getCodeMethodName(request)).thenReturn("doSomething");

    SpanNameExtractor<Object> underTest = CodeSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertThat(spanName).isEqualTo(getClass().getSimpleName() + "$$Lambda.doSomething");
  }

  @ParameterizedTest
  @MethodSource("generatedClassNames")
  void shouldExtractSpanNameForGeneratedClass(Class<?> codeClass, String expectedClassName) {
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> codeClass);
    when(getter.getCodeMethodName(request)).thenReturn("doSomething");

    assertThat(CodeSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(expectedClassName + ".doSomething");
  }

  @ParameterizedTest
  @MethodSource("generatedClassNames")
  void shouldExtractGeneratedClassWithoutMethod(Class<?> codeClass, String expectedClassName) {
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> codeClass);

    assertThat(CodeSpanNameExtractor.create(getter).extract(request)).isEqualTo(expectedClassName);
  }

  private static Stream<Arguments> generatedClassNames() {
    Object proxy =
        Proxy.newProxyInstance(
            CodeSpanNameExtractorTest.class.getClassLoader(),
            new Class<?>[] {Runnable.class},
            (instance, method, args) -> null);
    return Stream.of(
        Arguments.of(TestClass$$EnhancerBySpringCGLIB$$d662c0c3.class, "TestClass"),
        Arguments.of(TestClass$$SpringCGLIB$$0.class, "TestClass"),
        Arguments.of(proxy.getClass(), "$Proxy"),
        Arguments.of(TestClass$$anon$1.class, "TestClass$$anon$1"),
        Arguments.of(TestClass$$anonfun$1.class, "TestClass$$anonfun$1"),
        Arguments.of($Proxy119.class, "$Proxy119"),
        Arguments.of($ProxyHandler.class, "$ProxyHandler"));
  }

  @Test
  void shouldExtractClassWithoutMethod() {
    Object request = new Object();
    when(getter.getCodeClass(request)).thenAnswer(invocation -> TestClass.class);

    assertThat(CodeSpanNameExtractor.create(getter).extract(request)).isEqualTo("TestClass");
  }

  @Test
  void shouldExtractMethodWithoutClass() {
    Object request = new Object();
    when(getter.getCodeMethodName(request)).thenReturn("doSomething");

    assertThat(CodeSpanNameExtractor.create(getter).extract(request))
        .isEqualTo("<unknown>.doSomething");
  }

  @Test
  void shouldExtractUnknownNameIfNoneAreAvailable() {
    assertThat(CodeSpanNameExtractor.create(getter).extract(new Object())).isEqualTo("<unknown>");
  }

  static class TestClass {}

  static class AnonymousBaseClass {}

  static class TestClass$$EnhancerBySpringCGLIB$$d662c0c3 {}

  static class TestClass$$SpringCGLIB$$0 {}

  static class TestClass$$anon$1 {}

  static class TestClass$$anonfun$1 {}

  static class $Proxy119 {}

  static class $ProxyHandler {}
}
