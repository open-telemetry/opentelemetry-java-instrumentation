/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.SafeHasSuperTypeMatcher.safeGetSuperClass;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// TODO: add javadoc
class SafeExtendsClassMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super TypeDescription.Generic> matcher;

  public SafeExtendsClassMatcher(ElementMatcher<? super TypeDescription.Generic> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(T target) {
    // We do not use foreach loop and iterator interface here because we need to catch exceptions
    // in {@code getSuperClass} calls
    TypeDefinition typeDefinition = target;
    while (typeDefinition != null) {
      if (matcher.matches(typeDefinition.asGenericType())) {
        return true;
      }
      typeDefinition = safeGetSuperClass(typeDefinition);
    }
    return false;
  }

  @Override
  public String toString() {
    return "safeExtendsClass(" + matcher + ")";
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (getClass() != other.getClass()) {
      return false;
    } else {
      return matcher.equals(((SafeExtendsClassMatcher) other).matcher);
    }
  }

  @Override
  public int hashCode() {
    return 17 * 31 + matcher.hashCode();
  }
}
