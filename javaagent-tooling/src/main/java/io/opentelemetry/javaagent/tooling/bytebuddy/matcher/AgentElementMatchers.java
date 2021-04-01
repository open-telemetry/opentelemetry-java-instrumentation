/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This class provides some custom ByteBuddy element matchers to use when applying instrumentation.
 */
public class AgentElementMatchers {

  public static <T extends TypeDescription> ElementMatcher.Junction<T> extendsClass(
      ElementMatcher<? super TypeDescription> matcher) {
    return not(isInterface()).and(new SafeExtendsClassMatcher<>(new SafeErasureMatcher<>(matcher)));
  }

  public static <T extends TypeDescription> ElementMatcher.Junction<T> implementsInterface(
      ElementMatcher<? super TypeDescription> matcher) {
    return not(isInterface())
        .and(new SafeHasSuperTypeMatcher<>(new SafeErasureMatcher<>(matcher), true));
  }

  public static <T extends TypeDescription> ElementMatcher.Junction<T> hasInterface(
      ElementMatcher<? super TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher<>(new SafeErasureMatcher<>(matcher), true);
  }

  public static <T extends TypeDescription> ElementMatcher.Junction<T> safeHasSuperType(
      ElementMatcher<? super TypeDescription> matcher) {
    return not(isInterface())
        .and(new SafeHasSuperTypeMatcher<>(new SafeErasureMatcher<>(matcher), false));
  }

  /**
   * Matches method's declaring class against a given type matcher.
   *
   * @param matcher type matcher to match method's declaring type against.
   * @param <T> Type of the matched object
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
    return new LoggingFailSafeMatcher<>(matcher, false, description);
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
}
