/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This class provides some custom ByteBuddy element matchers to use when applying instrumentation.
 */
public final class AgentElementMatchers {

  public static ElementMatcher.Junction<TypeDescription> extendsClass(
      ElementMatcher<TypeDescription> matcher) {
    return not(isInterface()).and(new SafeExtendsClassMatcher(new SafeErasureMatcher<>(matcher)));
  }

  public static ElementMatcher.Junction<TypeDescription> implementsInterface(
      ElementMatcher<TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher(
        new SafeErasureMatcher<>(matcher), /* interfacesOnly= */ true);
  }

  public static ElementMatcher.Junction<TypeDescription> safeHasSuperType(
      ElementMatcher<TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher(
        new SafeErasureMatcher<>(matcher), /* interfacesOnly= */ false);
  }

  /**
   * Matches method's declaring class against a given type matcher.
   *
   * @param matcher type matcher to match method's declaring type against.
   * @param <T> Type of the matched object
   * @return a matcher that matches method's declaring class against a given type matcher.
   */
  public static <T extends MethodDescription> ElementMatcher.Junction<T> methodIsDeclaredByType(
      ElementMatcher<TypeDescription> matcher) {
    return new MethodDeclaringTypeMatcher<>(matcher);
  }

  /**
   * Matches a method and all its declarations up the class hierarchy including interfaces using
   * provided matcher.
   *
   * @param matcher method matcher to apply to method declarations up the hierarchy.
   * @param <T> Type of the matched object
   * @return A matcher that matches a method and all its declarations up the class hierarchy
   *     including interfaces.
   */
  public static <T extends MethodDescription> ElementMatcher.Junction<T> hasSuperMethod(
      ElementMatcher<? super MethodDescription> matcher) {
    return new HasSuperMethodMatcher<>(matcher);
  }

  /**
   * Wraps another matcher to assure that an element is not matched in case that the matching causes
   * an {@link Exception}. Logs exception if it happens.
   *
   * @param matcher The element matcher that potentially throws an exception.
   * @param <T> The type of the matched object.
   * @return A matcher that returns {@code false} in case that the given matcher throws an
   *     exception.
   */
  public static <T> ElementMatcher.Junction<T> failSafe(
      ElementMatcher<? super T> matcher, String description) {
    return new LoggingFailSafeMatcher<>(matcher, /* fallback= */ false, description);
  }

  static String safeTypeDefinitionName(TypeDefinition td) {
    try {
      return td.getTypeName();
    } catch (IllegalStateException ex) {
      String message = ex.getMessage();
      if (message.startsWith("Cannot resolve type description for ")) {
        return message.replace("Cannot resolve type description for ", "");
      } else {
        return "?";
      }
    }
  }

  private AgentElementMatchers() {}
}
