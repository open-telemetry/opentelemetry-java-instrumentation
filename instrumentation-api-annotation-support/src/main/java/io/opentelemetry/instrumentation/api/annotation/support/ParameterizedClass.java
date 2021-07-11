/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;
import java.util.Optional;

/**
 * Helper class for reflecting over the type hierarchy of parameterized types where the generic type
 * arguments are reified and/or mapped to the type parameters of the implemented superclasses or
 * interfaces.
 *
 * <p>The only way to walk the type hierarchy of a {@link ParameterizedType} is through {@link
 * ParameterizedType#getRawType} which returns an instance of {@link Class Class&lt;?&gt;} which
 * loses the mapping with the actual type arguments. This helper class tracks the association
 * between the type variables of the class with the type parameters of the superclass in order to
 * map the actual type arguments to those type parameters. This makes it possible to determine the
 * actual type arguments to the generic interfaces and superclasses in the type hierarchy.
 *
 * <p>For example, given the parameterized type {@link java.util.ArrayList ArrayList&lt;String&gt;}
 * you can determine that the superclass is {@link java.util.AbstractList
 * AbstractList&lt;String&gt;} which implements generic interfaces such as {@link java.util.List
 * List&lt;String&gt;} and {@link java.util.Collection Collection&lt;String&gt;}.
 */
final class ParameterizedClass {
  private final Class<?> rawClass;
  private final Type[] typeArguments;

  public static ParameterizedClass of(Type type) {
    Objects.requireNonNull(type);
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawClass = (Class<?>) parameterizedType.getRawType();
      return new ParameterizedClass(rawClass, parameterizedType.getActualTypeArguments());
    } else if (type instanceof Class) {
      Class<?> cls = (Class<?>) type;
      return new ParameterizedClass(cls, cls.getTypeParameters());
    }
    throw new IllegalArgumentException();
  }

  private ParameterizedClass(Class<?> rawClass, Type[] typeArguments) {
    this.rawClass = rawClass;
    this.typeArguments = typeArguments;
  }

  /** Gets the raw class of the parameterized class. */
  public Class<?> getRawClass() {
    return rawClass;
  }

  /** Gets the actual type arguments of the parameterized class. */
  public Type[] getActualTypeArguments() {
    return typeArguments.clone();
  }

  /** Gets the parameterized superclass of the current parameterized class. */
  public ParameterizedClass getParameterizedSuperclass() {
    return resolveSuperTypeActualTypeArguments(rawClass.getGenericSuperclass());
  }

  /** Gets an array of the parameterized interfaces of the current parameterized class. */
  public ParameterizedClass[] getParameterizedInterfaces() {
    Type[] interfaceTypes = rawClass.getGenericInterfaces();
    ParameterizedClass[] parameterizedClasses = new ParameterizedClass[interfaceTypes.length];
    for (int i = 0; i < interfaceTypes.length; i++) {
      parameterizedClasses[i] = resolveSuperTypeActualTypeArguments(interfaceTypes[i]);
    }
    return parameterizedClasses;
  }

  /**
   * Walks the type hierarchy of the parameterized class to determine if it extends from or
   * implements the specified super class.
   */
  public Optional<ParameterizedClass> findParameterizedSuperclass(Class<?> superClass) {
    Objects.requireNonNull(superClass);
    return findParameterizedSuperclassImpl(this, superClass);
  }

  private static Optional<ParameterizedClass> findParameterizedSuperclassImpl(
      ParameterizedClass current, Class<?> superClass) {

    if (current == null) {
      return Optional.empty();
    }
    if (current.rawClass.equals(superClass)) {
      return Optional.of(current);
    }
    if (superClass.isInterface()) {
      for (ParameterizedClass interfaceType : current.getParameterizedInterfaces()) {
        if (interfaceType.rawClass.equals(superClass)) {
          return Optional.of(interfaceType);
        }
      }
    }
    return findParameterizedSuperclassImpl(current.getParameterizedSuperclass(), superClass);
  }

  private ParameterizedClass resolveSuperTypeActualTypeArguments(Type superType) {
    if (superType instanceof ParameterizedType) {
      ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
      TypeVariable<?>[] typeParameters = rawClass.getTypeParameters();
      return resolveSuperTypeActualTypeArguments(
          parameterizedSuperType, typeParameters, typeArguments);
    } else if (superType != null) {
      return ParameterizedClass.of(superType);
    }
    return null;
  }

  /**
   * Maps the actual type arguments to the type parameters of the superclass based on the identity
   * of the type variables.
   */
  private static ParameterizedClass resolveSuperTypeActualTypeArguments(
      ParameterizedType parameterizedSuperType,
      TypeVariable<?>[] typeParameters,
      Type[] actualTypeArguments) {
    Type[] superTypeArguments = parameterizedSuperType.getActualTypeArguments();

    for (int i = 0; i < superTypeArguments.length; i++) {
      Type superTypeArgument = superTypeArguments[i];
      if (superTypeArgument instanceof TypeVariable) {
        TypeVariable<?> superTypeVariable = (TypeVariable<?>) superTypeArgument;
        superTypeArguments[i] =
            mapTypeVariableToActualTypeArgument(
                superTypeVariable, typeParameters, actualTypeArguments);
      }
    }
    Class<?> rawSuperClass = (Class<?>) parameterizedSuperType.getRawType();
    return new ParameterizedClass(rawSuperClass, superTypeArguments);
  }

  private static Type mapTypeVariableToActualTypeArgument(
      TypeVariable<?> superTypeVariable,
      TypeVariable<?>[] typeParameters,
      Type[] actualTypeArguments) {
    for (int i = 0; i < typeParameters.length; i++) {
      if (equalsTypeVariable(superTypeVariable, typeParameters[i])) {
        return actualTypeArguments[i];
      }
    }
    return superTypeVariable;
  }

  /**
   * Ensures that any two type variables are equal as long as they are declared by the same {@link
   * java.lang.reflect.GenericDeclaration} and have the same name, even if their bounds differ.
   *
   * <p>While resolving a type variable from a {@code var -> type} map, we don't care whether the
   * type variable's bound has been partially resolved. As long as the type variable "identity"
   * matches.
   *
   * <p>On the other hand, if for example we are resolving {@code List<A extends B>} to {@code
   * List<A extends String>}, we need to compare that {@code <A extends B>} is unequal to {@code <A
   * extends String>} in order to decide to use the transformed type instead of the original type.
   */
  private static boolean equalsTypeVariable(TypeVariable<?> left, TypeVariable<?> right) {
    if (left == right) {
      return true;
    } else if (left == null || right == null) {
      return false;
    }
    return left.getGenericDeclaration().equals(right.getGenericDeclaration())
        && left.getName().equals(right.getName());
  }
}
