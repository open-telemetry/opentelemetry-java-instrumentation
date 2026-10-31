/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArgumentsNamed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
class TakesArgumentsNamedMatcherTest {

  @Test
  void exactNamesMatch() throws NoSuchMethodException {
    assertThat(
            matches(
                "exact",
                new Class<?>[] {String.class, Integer.class},
                "java.lang.String",
                "java.lang.Integer"))
        .isTrue();
  }

  @Test
  void wrongTypeDoesNotMatch() throws NoSuchMethodException {
    assertThat(
            matches(
                "wrongType",
                new Class<?>[] {String.class, Long.class},
                "java.lang.String",
                "java.lang.Integer"))
        .isFalse();
  }

  @Test
  void parameterOrderMatters() throws NoSuchMethodException {
    assertThat(
            matches(
                "reversed",
                new Class<?>[] {Integer.class, String.class},
                "java.lang.String",
                "java.lang.Integer"))
        .isFalse();
  }

  @Test
  void tooFewParametersDoNotMatch() throws NoSuchMethodException {
    assertThat(
            matches(
                "tooFew", new Class<?>[] {String.class}, "java.lang.String", "java.lang.Integer"))
        .isFalse();
  }

  @Test
  void tooManyParametersDoNotMatch() throws NoSuchMethodException {
    assertThat(
            matches(
                "tooMany",
                new Class<?>[] {String.class, Integer.class, Long.class},
                "java.lang.String",
                "java.lang.Integer"))
        .isFalse();
  }

  @Test
  void primitiveNamesMatch() throws NoSuchMethodException {
    assertThat(matches("primitives", new Class<?>[] {int.class, boolean.class}, "int", "boolean"))
        .isTrue();
  }

  @Test
  void emptyNamesMatchOnlyEmptyParameterLists() throws NoSuchMethodException {
    assertThat(matches("noArguments", new Class<?>[0])).isTrue();
    assertThat(matches("tooFew", new Class<?>[] {String.class})).isFalse();
  }

  @Test
  void genericParametersMatchTheirErasedNames() throws NoSuchMethodException {
    assertThat(matches("generic", new Class<?>[] {List.class}, "java.util.List")).isTrue();
  }

  @Test
  void expectedNamesAreCopiedOnConstruction() throws NoSuchMethodException {
    String[] names = {"java.lang.String"};
    ElementMatcher.Junction<MethodDescription> matcher = takesArgumentsNamed(names);
    names[0] = "java.lang.Integer";

    assertThat(matches(matcher, "string", new Class<?>[] {String.class})).isTrue();
  }

  @Test
  void wrongArityRejectsBeforeAccessingParameterTypes() {
    MethodDescription method = mock(MethodDescription.class);
    ParameterList<?> parameters = mock(ParameterList.class);
    when(method.getParameters()).thenAnswer(invocation -> parameters);
    when(parameters.size()).thenReturn(1);

    assertThat(takesArgumentsNamed("java.lang.String", "java.lang.Integer").matches(method))
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
    TypeDescription.Generic firstType = mock(TypeDescription.Generic.class);
    TypeDescription erasedFirstType = mock(TypeDescription.class);
    when(method.getParameters()).thenAnswer(invocation -> parameters);
    when(parameters.size()).thenReturn(2);
    when(parameters.asTypeList()).thenReturn(parameterTypes);
    when(parameterTypes.get(0)).thenReturn(firstType);
    when(firstType.asErasure()).thenReturn(erasedFirstType);
    when(erasedFirstType.getName()).thenReturn("java.lang.Long");

    assertThat(takesArgumentsNamed("java.lang.String", "java.lang.Integer").matches(method))
        .isFalse();

    verify(parameterTypes).get(0);
    verifyNoMoreInteractions(parameterTypes);
  }

  private static boolean matches(
      String methodName, Class<?>[] parameterTypes, String... parameterNames)
      throws NoSuchMethodException {
    return matches(takesArgumentsNamed(parameterNames), methodName, parameterTypes);
  }

  private static boolean matches(
      ElementMatcher.Junction<MethodDescription> matcher,
      String methodName,
      Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    Method method =
        TakesArgumentsNamedMatcherTest.class.getDeclaredMethod(methodName, parameterTypes);
    MethodDescription description = new MethodDescription.ForLoadedMethod(method);
    return matcher.matches(description);
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
