/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.C;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.Trace;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.TracedClass;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.UntracedClass;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import net.bytebuddy.description.method.MethodDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HasSuperMethodMatcherTest {

  private static Stream<Arguments> matcherParameters() {
    return Stream.of(
        Arguments.of(A.class, "a", false),
        Arguments.of(B.class, "b", true),
        Arguments.of(C.class, "c", false),
        Arguments.of(F.class, "f", true),
        Arguments.of(G.class, "g", false),
        Arguments.of(TracedClass.class, "a", true),
        Arguments.of(UntracedClass.class, "a", false),
        Arguments.of(UntracedClass.class, "b", true));
  }

  @ParameterizedTest
  @MethodSource("matcherParameters")
  void testMatcher(Class<?> clazz, String methodName, boolean expectedResult) throws Exception {
    MethodDescription argument =
        new MethodDescription.ForLoadedMethod(clazz.getDeclaredMethod(methodName));

    boolean result = hasSuperMethod(isAnnotatedWith(Trace.class)).matches(argument);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void testCovariantInterfaceMethod() {
    Method implementation =
        method(CovariantInterfaceImplementation.class, String.class, String.class, false);
    Method bridge =
        method(CovariantInterfaceImplementation.class, Object.class, String.class, true);
    Method overload =
        method(CovariantInterfaceImplementation.class, String.class, Integer.class, false);

    assertThat(
            hasSuperMethod(isAnnotatedWith(Trace.class))
                .matches(new MethodDescription.ForLoadedMethod(implementation)))
        .isTrue();
    assertThat(
            hasSuperMethod(isAnnotatedWith(Trace.class))
                .matches(new MethodDescription.ForLoadedMethod(bridge)))
        .isFalse();
    assertThat(
            hasSuperMethod(isAnnotatedWith(Trace.class))
                .matches(new MethodDescription.ForLoadedMethod(overload)))
        .isFalse();
  }

  @Test
  void testCovariantSuperclassMethod() {
    Method implementation =
        method(CovariantSuperclassImplementation.class, String.class, String.class, false);

    assertThat(
            hasSuperMethod(isAnnotatedWith(Trace.class))
                .matches(new MethodDescription.ForLoadedMethod(implementation)))
        .isTrue();
  }

  @Test
  void testConstructorNeverMatches() {
    MethodDescription method = mock(MethodDescription.class);
    when(method.isConstructor()).thenReturn(true);

    boolean result = hasSuperMethod(none()).matches(method);

    assertThat(result).isFalse();
    verify(method, times(1)).isConstructor();
    verifyNoMoreInteractions(method);
  }

  @Test
  void testTraversalExceptions() {
    MethodDescription method = mock(MethodDescription.class);

    when(method.isConstructor()).thenReturn(false);
    when(method.isBridge()).thenReturn(false);
    when(method.getDeclaringType()).thenReturn(null);

    boolean result = hasSuperMethod(none()).matches(method);

    assertThat(result).isFalse();
    verify(method, times(1)).isConstructor();
    verify(method, times(1)).isBridge();
    verify(method, times(1)).getDeclaringType();
    verifyNoMoreInteractions(method);
  }

  private static Method method(
      Class<?> type, Class<?> returnType, Class<?> parameterType, boolean bridge) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(method -> method.getReturnType().equals(returnType))
        .filter(method -> method.getParameterTypes()[0].equals(parameterType))
        .filter(method -> method.isBridge() == bridge)
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  interface CovariantInterface {
    @Trace
    Object get(String value);
  }

  static class CovariantInterfaceImplementation implements CovariantInterface {
    @Override
    public String get(String value) {
      return value;
    }

    public String get(Integer value) {
      return value.toString();
    }
  }

  static class CovariantSuperclass {
    @Trace
    public Object get(String value) {
      return value;
    }
  }

  static class CovariantSuperclassImplementation extends CovariantSuperclass {
    @Override
    public String get(String value) {
      return value;
    }
  }
}
