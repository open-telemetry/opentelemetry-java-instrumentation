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
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
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
    TypeDefinition targetType = target.getDeclaringType();
    TypeDefinition declaringType = targetType;
    Set<TypeDefinition> checkedInterfaces = new HashSet<>(8);

    while (declaringType != null) {
      boolean isTargetType = declaringType == targetType;
      for (MethodDescription methodDescription : declaringType.getDeclaredMethods()) {
        if ((isTargetType
                ? hasMatchingSignature(target, methodDescription)
                : isOverriddenBy(target, methodDescription))
            && matcher.matches(methodDescription)) {
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
          if (isOverriddenBy(target, methodDescription) && matcher.matches(methodDescription)) {
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

  private static boolean isOverriddenBy(MethodDescription target, MethodDescription candidate) {
    if (target.isStatic()
        || target.isPrivate()
        || candidate.isStatic()
        || candidate.isPrivate()
        || candidate.isBridge()
        || !(candidate.isVisibleTo(target.getDeclaringType().asErasure())
            || isOverriddenViaSamePackageClass(target, candidate))) {
      return false;
    }
    return hasMatchingSignature(target, candidate) || isBridgedTo(target, candidate);
  }

  private static boolean isOverriddenViaSamePackageClass(
      MethodDescription target, MethodDescription candidate) {
    TypeDescription candidateType = candidate.getDeclaringType().asErasure();
    for (TypeDefinition type = safeGetSuperClass(target.getDeclaringType());
        type != null && !type.asErasure().equals(candidateType);
        type = safeGetSuperClass(type)) {
      if (type.asErasure().isSamePackage(candidateType)
          && declaresOverride(type, target, target.getDeclaringType().asErasure())) {
        return true;
      }
    }
    return false;
  }

  private static boolean declaresOverride(
      TypeDefinition type, MethodDescription target, TypeDescription targetType) {
    for (MethodDescription declared : type.getDeclaredMethods()) {
      if (declared.isMethod()
          && !declared.isBridge()
          && !declared.isStatic()
          && !declared.isPrivate()
          && declared.isVisibleTo(targetType)
          && declared.getInternalName().equals(target.getInternalName())
          && haveSameParameterTypes(declared, target)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBridgedTo(MethodDescription target, MethodDescription candidate) {
    if (!target.getInternalName().equals(candidate.getInternalName())) {
      return false;
    }
    for (TypeDefinition type = target.getDeclaringType();
        type != null;
        type = safeGetSuperClass(type)) {
      for (MethodDescription bridge : type.getDeclaredMethods()) {
        if (bridge.isBridge()
            && bridge.getInternalName().equals(candidate.getInternalName())
            && haveSameParameterTypes(bridge, candidate)
            && areParametersAssignable(bridge.getParameters(), target.getParameters())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean areParametersAssignable(
      ParameterList<?> bridgeParameters, ParameterList<?> targetParameters) {
    if (bridgeParameters.size() != targetParameters.size()) {
      return false;
    }
    for (int i = 0; i < targetParameters.size(); i++) {
      TypeDescription bridgeType = bridgeParameters.get(i).getType().asErasure();
      TypeDescription targetType = targetParameters.get(i).getType().asErasure();
      if (!targetType.isAssignableTo(bridgeType)) {
        return false;
      }
    }
    return true;
  }

  private static boolean haveSameParameterTypes(MethodDescription first, MethodDescription second) {
    return first
        .asSignatureToken()
        .getParameterTypes()
        .equals(second.asSignatureToken().getParameterTypes());
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
