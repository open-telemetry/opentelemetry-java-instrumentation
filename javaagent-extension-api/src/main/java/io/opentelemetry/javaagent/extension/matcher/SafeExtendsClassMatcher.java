/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static io.opentelemetry.javaagent.extension.matcher.SafeHasSuperTypeMatcher.safeGetSuperClass;

import javax.annotation.Nullable;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SafeExtendsClassMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {

  private final ElementMatcher<TypeDescription.Generic> matcher;

  public SafeExtendsClassMatcher(ElementMatcher<TypeDescription.Generic> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(TypeDescription target) {
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
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SafeExtendsClassMatcher)) {
      return false;
    }
    SafeExtendsClassMatcher other = (SafeExtendsClassMatcher) obj;
    return matcher.equals(other.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
