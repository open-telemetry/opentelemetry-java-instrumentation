/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static io.opentelemetry.javaagent.extension.matcher.SafeHasSuperTypeMatcher.safeGetSuperClass;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Matches a method and all its declarations up the class hierarchy including interfaces using
 * provided matcher.
 *
 * @param <T> Type of the matched method.
 */
class HasSuperMethodMatcher<T extends MethodDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super MethodDescription> matcher;

  public HasSuperMethodMatcher(ElementMatcher<? super MethodDescription> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(MethodDescription target) {
    if (target.isConstructor() || target.isBridge()) {
      return false;
    }
    TypeDefinition declaringType = target.getDeclaringType();
    Set<TypeDefinition> checkedInterfaces = new HashSet<>(8);

    while (declaringType != null) {
      for (MethodDescription methodDescription : declaringType.getDeclaredMethods()) {
        if (hasMatchingSignature(target, methodDescription) && matcher.matches(methodDescription)) {
          return true;
        }
      }
      if (matchesInterface(declaringType.getInterfaces(), target, checkedInterfaces)) {
        return true;
      }
      declaringType = safeGetSuperClass(declaringType);
    }
    return false;
  }

  private boolean matchesInterface(
      TypeList.Generic interfaces,
      MethodDescription target,
      Set<TypeDefinition> checkedInterfaces) {
    for (TypeDefinition type : interfaces) {
      if (checkedInterfaces.add(type)) {
        for (MethodDescription methodDescription : type.getDeclaredMethods()) {
          if (hasMatchingSignature(target, methodDescription)
              && matcher.matches(methodDescription)) {
            return true;
          }
        }
        if (matchesInterface(type.getInterfaces(), target, checkedInterfaces)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasMatchingSignature(
      MethodDescription target, MethodDescription candidate) {
    MethodDescription.SignatureToken targetToken = target.asSignatureToken();
    MethodDescription.SignatureToken candidateToken = candidate.asSignatureToken();
    if (!targetToken.getName().equals(candidateToken.getName())
        || !targetToken.getParameterTypes().equals(candidateToken.getParameterTypes())) {
      return false;
    }

    if (targetToken.getReturnType().equals(candidateToken.getReturnType())) {
      return true;
    }

    // Allow covariant return types: target is the overriding method, so its return type
    // may be a subtype of candidate's return type. This requires an accessible instance
    // method that can be overridden.
    return !target.isStatic()
        && !candidate.isStatic()
        && candidate.isVisibleTo(target.getDeclaringType().asErasure())
        && target.getReturnType().asErasure().isAssignableTo(candidate.getReturnType().asErasure());
  }

  @Override
  public String toString() {
    return "hasSuperMethodMatcher(" + matcher + ")";
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof HasSuperMethodMatcher)) {
      return false;
    }
    HasSuperMethodMatcher<?> other = (HasSuperMethodMatcher<?>) obj;
    return matcher.equals(other.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
