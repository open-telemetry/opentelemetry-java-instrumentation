/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArgumentsExactly;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
class TakesArgumentsExactlyMatcherTest {

  @ParameterizedTest
  @MethodSource("signatureCases")
  void matchesSignature(String methodName, String[] parameterNames, boolean expected) {
    assertThat(takesArgumentsExactly(parameterNames).matches(method(methodName)))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> signatureCases() {
    return Stream.of(
        argumentSet(
            "exact names", "exact", new String[] {"java.lang.String", "java.lang.Integer"}, true),
        argumentSet(
            "wrong type",
            "wrongType",
            new String[] {"java.lang.String", "java.lang.Integer"},
            false),
        argumentSet(
            "parameter order",
            "reversed",
            new String[] {"java.lang.String", "java.lang.Integer"},
            false),
        argumentSet(
            "too few actual parameters",
            "tooFew",
            new String[] {"java.lang.String", "java.lang.Integer"},
            false),
        argumentSet(
            "too many actual parameters",
            "tooMany",
            new String[] {"java.lang.String", "java.lang.Integer"},
            false),
        argumentSet("primitive names", "primitives", new String[] {"int", "boolean"}, true),
        argumentSet("empty names and signature", "noArguments", new String[0], true),
        argumentSet("empty names and nonempty signature", "string", new String[0], false),
        argumentSet("generic erasure", "generic", new String[] {"java.util.List"}, true));
  }

  @Test
  void expectedNamesAreCopiedOnConstruction() {
    String[] names = {"java.lang.String"};
    ElementMatcher.Junction<MethodDescription> matcher = takesArgumentsExactly(names);
    names[0] = "java.lang.Integer";

    assertThat(matcher.matches(method("string"))).isTrue();
  }

  @Test
  void wrongArityRejectsBeforeAccessingParameterTypes() {
    MethodDescription method = mock(MethodDescription.class);
    ParameterList<?> parameters = mock(ParameterList.class);
    when(method.getParameters()).thenAnswer(invocation -> parameters);
    when(parameters.size()).thenReturn(1);

    assertThat(takesArgumentsExactly("java.lang.String", "java.lang.Integer").matches(method))
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

    assertThat(takesArgumentsExactly("java.lang.String", "java.lang.Integer").matches(method))
        .isFalse();

    verify(parameterTypes).get(0);
    verifyNoMoreInteractions(parameterTypes);
  }

  private static MethodDescription method(String methodName) {
    return new TypeDescription.ForLoadedType(TakesArgumentsExactlyMatcherTest.class)
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

  private static void noArguments() {}

  private static void generic(List<String> values) {}

  private static void string(String value) {}
}
