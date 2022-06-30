/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This class is a supplement to ByteBuddy's {@link net.bytebuddy.matcher.ElementMatchers} - it
 * provides some custom matcher implementations that might be useful for instrumentation purposes.
 */
public final class AgentElementMatchers {

  /**
   * Matches a type that extends a class (directly or indirectly) that matches the provided {@code
   * matcher}.
   *
   * <p>The returned matcher will not throw anything when walking the type hierarchy fails; instead
   * it will return {@code false} for the passed type description.
   */
  public static ElementMatcher.Junction<TypeDescription> extendsClass(
      ElementMatcher<TypeDescription> matcher) {
    return not(isInterface()).and(new SafeExtendsClassMatcher(new SafeErasureMatcher<>(matcher)));
  }

  /**
   * Matches a type that implements an interface (directly or indirectly) that matches the provided
   * {@code matcher}.
   *
   * <p>The returned matcher will not throw anything when walking the type hierarchy fails; instead
   * it will return {@code false} for the passed type description.
   */
  public static ElementMatcher.Junction<TypeDescription> implementsInterface(
      ElementMatcher<TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher(
        new SafeErasureMatcher<>(matcher), /* interfacesOnly= */ true);
  }

  /**
   * Matches a type that extends or implements a type (directly or indirectly) that matches the
   * provided {@code matcher}.
   *
   * <p>The returned matcher will not throw anything when walking the type hierarchy fails; instead
   * it will return {@code false} for the passed type description.
   */
  public static ElementMatcher.Junction<TypeDescription> hasSuperType(
      ElementMatcher<TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher(
        new SafeErasureMatcher<>(matcher), /* interfacesOnly= */ false);
  }

  /**
   * Matches method's declaring class against a given type matcher.
   *
   * @param matcher type matcher to match method's declaring type against.
   * @return a matcher that matches method's declaring class against a given type matcher.
   */
  public static <T extends MethodDescription> ElementMatcher.Junction<T> methodIsDeclaredByType(
      ElementMatcher<? super TypeDescription> matcher) {
    return new MethodDeclaringTypeMatcher<>(matcher);
  }

  /**
   * Matches a method and all its declarations up the class hierarchy including interfaces using
   * provided matcher.
   *
   * @param matcher method matcher to apply to method declarations up the hierarchy.
   * @return A matcher that matches a method and all its declarations up the class hierarchy
   *     including interfaces.
   */
  public static <T extends MethodDescription> ElementMatcher.Junction<T> hasSuperMethod(
      ElementMatcher<? super MethodDescription> matcher) {
    return new HasSuperMethodMatcher<>(matcher);
  }

  /**
   * Matches a class loader that contains all classes that are passed as the {@code classNames}
   * parameter. Does not match the bootstrap classpath. Don't use this matcher with classes expected
   * to be on the bootstrap.
   *
   * <p>In the event no class names are passed at all, the matcher will always return {@code true}.
   *
   * @param classNames list of class names to match.
   */
  public static ElementMatcher.Junction<ClassLoader> hasClassesNamed(String... classNames) {
    return new ClassLoaderHasClassesNamedMatcher(classNames);
  }

  private AgentElementMatchers() {}
}
