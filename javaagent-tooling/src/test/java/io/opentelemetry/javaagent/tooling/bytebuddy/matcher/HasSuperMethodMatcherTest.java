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
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.PackagePrivateBase;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.PackagePrivateOverrideOfPackagePrivateBase;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.PublicOverrideOfPackagePrivateBase;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.SamePackageSubclass;
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
  void testGenericInterfaceMethodImplementedWithConcreteType() throws Exception {
    assertThat(matches(StringHandler.class, "handle", String.class)).isTrue();
  }

  @Test
  void testGenericInterfaceMethodOverriddenInSubclassOfImplementation() throws Exception {
    assertThat(matches(ExtendedStringHandler.class, "handle", String.class)).isTrue();
  }

  @Test
  void testPrivateSuperMethodIsNotOverridden() throws Exception {
    assertThat(matches(PrivateBase.class, "work")).isTrue();
    assertThat(matches(PrivateSubclass.class, "work")).isFalse();
  }

  @Test
  void testStaticSuperMethodIsNotOverridden() throws Exception {
    assertThat(matches(StaticBase.class, "run")).isTrue();
    assertThat(matches(StaticSubclass.class, "run")).isFalse();
  }

  @Test
  void testPackagePrivateSuperMethodIsOnlyOverriddenFromSamePackage() throws Exception {
    assertThat(matches(SamePackageSubclass.class, "run")).isTrue();
    assertThat(matches(OtherPackageSubclass.class, "run")).isFalse();
  }

  @Test
  void testPackagePrivateSuperMethodIsOverriddenThroughPublicOverride() throws Exception {
    assertThat(matches(OverrideOfPublicOverride.class, "run")).isTrue();
  }

  @Test
  void testPackagePrivateSuperMethodIsNotOverriddenThroughPackagePrivateOverride()
      throws Exception {
    assertThat(matches(OverrideOfPackagePrivateOverride.class, "run")).isFalse();
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

  private static boolean matches(Class<?> type, String name, Class<?>... parameterTypes)
      throws Exception {
    return hasSuperMethod(isAnnotatedWith(Trace.class))
        .matches(
            new MethodDescription.ForLoadedMethod(type.getDeclaredMethod(name, parameterTypes)));
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

  interface GenericHandler<T> {
    @Trace
    void handle(T value);
  }

  static class StringHandler implements GenericHandler<String> {
    @Override
    public void handle(String value) {}

    public void handle(Integer value) {}
  }

  static class ExtendedStringHandler extends StringHandler {
    @Override
    public void handle(String value) {}
  }

  @SuppressWarnings({"unused", "MethodCanBeStatic"})
  static class PrivateBase {
    @Trace
    private void work() {}
  }

  @SuppressWarnings({"unused", "MethodCanBeStatic"})
  static class PrivateSubclass extends PrivateBase {
    private void work() {}
  }

  @SuppressWarnings("OtelPrivateConstructorForUtilityClass")
  static class StaticBase {
    @Trace
    static void run() {}
  }

  @SuppressWarnings("OtelPrivateConstructorForUtilityClass")
  static class StaticSubclass extends StaticBase {
    static void run() {}
  }

  static class OverrideOfPublicOverride extends PublicOverrideOfPackagePrivateBase {
    @Override
    public void run() {}
  }

  static class OverrideOfPackagePrivateOverride extends PackagePrivateOverrideOfPackagePrivateBase {
    public void run() {}
  }

  static class OtherPackageSubclass extends PackagePrivateBase {
    void run() {}
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
