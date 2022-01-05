/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import java.util.Objects;
import javax.annotation.Nullable;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Matches method's declaring class against a given type matcher.
 *
 * @param <T> Type of the matched object
 */
class MethodDeclaringTypeMatcher<T extends MethodDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super TypeDescription> matcher;

  MethodDeclaringTypeMatcher(ElementMatcher<? super TypeDescription> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(T target) {
    return matcher.matches(target.getDeclaringType().asErasure());
  }

  @Override
  public String toString() {
    return "methodDeclaringTypeMatcher(matcher=" + matcher + ')';
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodDeclaringTypeMatcher)) {
      return false;
    }
    MethodDeclaringTypeMatcher<?> that = (MethodDeclaringTypeMatcher<?>) o;
    return Objects.equals(matcher, that.matcher);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher);
  }
}
