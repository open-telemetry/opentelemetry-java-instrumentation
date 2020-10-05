/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.SafeHasSuperTypeMatcher.safeGetSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSignature;

import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

// TODO: add javadoc
class HasSuperMethodMatcher<T extends MethodDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super MethodDescription> matcher;

  public HasSuperMethodMatcher(ElementMatcher<? super MethodDescription> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(MethodDescription target) {
    if (target.isConstructor()) {
      return false;
    }
    Junction<MethodDescription> signatureMatcher = hasSignature(target.asSignatureToken());
    TypeDefinition declaringType = target.getDeclaringType();
    Set<TypeDefinition> checkedInterfaces = new HashSet<>(8);

    while (declaringType != null) {
      for (MethodDescription methodDescription : declaringType.getDeclaredMethods()) {
        if (signatureMatcher.matches(methodDescription) && matcher.matches(methodDescription)) {
          return true;
        }
      }
      if (matchesInterface(declaringType.getInterfaces(), signatureMatcher, checkedInterfaces)) {
        return true;
      }
      declaringType = safeGetSuperClass(declaringType);
    }
    return false;
  }

  private boolean matchesInterface(
      TypeList.Generic interfaces,
      Junction<MethodDescription> signatureMatcher,
      Set<TypeDefinition> checkedInterfaces) {
    for (TypeDefinition type : interfaces) {
      if (checkedInterfaces.add(type)) {
        for (MethodDescription methodDescription : type.getDeclaredMethods()) {
          if (signatureMatcher.matches(methodDescription) && matcher.matches(methodDescription)) {
            return true;
          }
        }
        if (matchesInterface(type.getInterfaces(), signatureMatcher, checkedInterfaces)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "hasSuperMethodMatcher(" + matcher + ")";
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
      return matcher.equals(((HasSuperMethodMatcher) other).matcher);
    }
  }

  @Override
  public int hashCode() {
    return 17 * 31 + matcher.hashCode();
  }
}
