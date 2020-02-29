/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling.bytebuddy.matcher;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * This class provides some custom ByteBuddy element matchers to use when applying instrumentation
 */
@Slf4j
public class AgentElementMatchers {

  public static <T extends TypeDescription> ElementMatcher.Junction<T> extendsClass(
      final ElementMatcher<? super TypeDescription> matcher) {
    return new SafeExtendsClassMatcher<>(new SafeErasureMatcher<>(matcher));
  }

  public static <T extends TypeDescription> ElementMatcher.Junction<T> hasInterface(
      final ElementMatcher<? super TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher<>(new SafeErasureMatcher<>(matcher), true);
  }

  /**
   * Matches any type description that declares a super type that matches the provided matcher.
   * Exceptions during matching process are logged and ignored.
   *
   * @param matcher The type to be checked for being a super type of the matched type.
   * @param <T> The type of the matched object.
   * @return A matcher that matches any type description that declares a super type that matches the
   *     provided matcher.
   * @see ElementMatchers#hasSuperType(net.bytebuddy.matcher.ElementMatcher)
   */
  public static <T extends TypeDescription> ElementMatcher.Junction<T> safeHasSuperType(
      final ElementMatcher<? super TypeDescription> matcher) {
    return new SafeHasSuperTypeMatcher<>(new SafeErasureMatcher<>(matcher), false);
  }
  // TODO: add javadoc
  public static <T extends MethodDescription> ElementMatcher.Junction<T> hasSuperMethod(
      final ElementMatcher<? super MethodDescription> matcher) {
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
      final ElementMatcher<? super T> matcher, final String description) {
    return new LoggingFailSafeMatcher<>(matcher, false, description);
  }

  static String safeTypeDefinitionName(final TypeDefinition td) {
    try {
      return td.getTypeName();
    } catch (final IllegalStateException ex) {
      final String message = ex.getMessage();
      if (message.startsWith("Cannot resolve type description for ")) {
        return message.replace("Cannot resolve type description for ", "");
      } else {
        return "?";
      }
    }
  }
}
