/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
class TakesArgumentsMatcherTest {

  @ParameterizedTest
  @MethodSource("signatureCases")
  void matchesSignature(
      String methodName,
      ElementMatcher<? super TypeDescription>[] parameterMatchers,
      boolean expected) {
    assertThat(takesArguments(parameterMatchers).matches(method(methodName))).isEqualTo(expected);
  }

  private static Stream<Arguments> signatureCases() {
    return Stream.of(
        argumentSet(
            "exact names",
            "exact",
            matchers(named("java.lang.String"), named("java.lang.Integer")),
            true),
        argumentSet(
            "wrong type",
            "wrongType",
            matchers(named("java.lang.String"), named("java.lang.Integer")),
            false),
        argumentSet(
            "parameter order",
            "reversed",
            matchers(named("java.lang.String"), named("java.lang.Integer")),
            false),
        argumentSet(
            "too few actual parameters",
            "tooFew",
            matchers(named("java.lang.String"), named("java.lang.Integer")),
            false),
        argumentSet(
            "too many actual parameters",
            "tooMany",
            matchers(named("java.lang.String"), named("java.lang.Integer")),
            false),
        argumentSet(
            "primitive types", "primitives", matchers(is(int.class), is(boolean.class)), true),
        argumentSet("primitive array", "primitiveArray", matchers(is(byte[].class)), true),
        argumentSet("reference array", "referenceArray", matchers(is(Object[].class)), true),
        argumentSet(
            "multidimensional array", "multidimensionalArray", matchers(is(byte[][].class)), true),
        argumentSet(
            "wrong array component type", "primitiveArray", matchers(is(int[].class)), false),
        argumentSet(
            "wrong array dimension", "multidimensionalArray", matchers(is(byte[].class)), false),
        argumentSet("empty matchers and signature", "noArguments", matchers(), true),
        argumentSet("empty matchers and nonempty signature", "string", matchers(), false),
        argumentSet("generic erasure", "generic", matchers(named("java.util.List")), true),
        argumentSet(
            "mixed name and class matchers",
            "mixed",
            matchers(named("java.lang.String"), is(int.class)),
            true),
        argumentSet(
            "multiple accepted names",
            "string",
            matchers(namedOneOf("java.lang.String", "java.lang.Long")),
            true));
  }

  @Test
  void expectedMatchersAreCopiedOnConstruction() {
    ElementMatcher<? super TypeDescription>[] matchers = matchers(named("java.lang.String"));
    ElementMatcher.Junction<MethodDescription> matcher = takesArguments(matchers);
    matchers[0] = named("java.lang.Integer");

    assertThat(matcher.matches(method("string"))).isTrue();
  }

  @Test
  void wrongArityRejectsBeforeAccessingParameterTypes() {
    MethodDescription method = mock(MethodDescription.class);
    ParameterList<?> parameters = mock(ParameterList.class);
    when(method.getParameters()).thenAnswer(invocation -> parameters);
    when(parameters.size()).thenReturn(1);

    assertThat(
            takesArguments(named("java.lang.String"), named("java.lang.Integer")).matches(method))
        .isFalse();

    verify(method).getParameters();
    verify(parameters).size();
    verifyNoMoreInteractions(method, parameters);
  }

  @Test
  void typeMismatchStopsFurtherTypeExtraction() {
    MethodDescription method = mock(MethodDescription.class);
    ParameterList<?> parameters = mock(ParameterList.class);
    TypeList.Generic parameterTypes = mock(TypeList.Generic.class);
    when(method.getParameters()).thenAnswer(invocation -> parameters);
    when(parameters.size()).thenReturn(2);
    when(parameters.asTypeList()).thenReturn(parameterTypes);
    when(parameterTypes.get(0))
        .thenReturn(new TypeDescription.ForLoadedType(Long.class).asGenericType());

    assertThat(
            takesArguments(named("java.lang.String"), named("java.lang.Integer")).matches(method))
        .isFalse();

    verify(parameterTypes).get(0);
    verifyNoMoreInteractions(parameterTypes);
  }

  private static MethodDescription method(String methodName) {
    return new TypeDescription.ForLoadedType(TakesArgumentsMatcherTest.class)
        .getDeclaredMethods()
        .filter(named(methodName))
        .getOnly();
  }

  private static void exact(String string, Integer integer) {}

  private static void wrongType(String string, Long number) {}

  private static void reversed(Integer integer, String string) {}

  private static void tooFew(String string) {}

  private static void tooMany(String string, Integer integer, Long number) {}

  private static void primitives(int integer, boolean bool) {}

  private static void primitiveArray(byte[] values) {}

  private static void referenceArray(Object[] values) {}

  private static void multidimensionalArray(byte[][] values) {}

  private static void noArguments() {}

  private static void generic(List<String> values) {}

  private static void string(String value) {}

  private static void mixed(String value, int number) {}

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static ElementMatcher<? super TypeDescription>[] matchers(
      ElementMatcher<? super TypeDescription>... matchers) {
    return matchers;
  }
}
