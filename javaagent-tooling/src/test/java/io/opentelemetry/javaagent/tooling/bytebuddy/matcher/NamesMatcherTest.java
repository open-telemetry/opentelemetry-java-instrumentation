/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.names;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
class NamesMatcherTest {

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

  private static boolean matches(
      String methodName, Class<?>[] parameterTypes, String... parameterNames)
      throws NoSuchMethodException {
    Method method = NamesMatcherTest.class.getDeclaredMethod(methodName, parameterTypes);
    MethodDescription description = new MethodDescription.ForLoadedMethod(method);
    return takesArguments(names(parameterNames)).matches(description);
  }

  private static void exact(String string, Integer integer) {}

  private static void wrongType(String string, Long number) {}

  private static void reversed(Integer integer, String string) {}

  private static void tooFew(String string) {}

  private static void tooMany(String string, Integer integer, Long number) {}

  private static void primitives(int integer, boolean bool) {}

  private static void noArguments() {}

  private static void generic(List<String> values) {}
}
