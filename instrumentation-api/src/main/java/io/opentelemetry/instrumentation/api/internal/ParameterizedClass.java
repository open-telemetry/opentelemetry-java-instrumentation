/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;
import java.util.Optional;

/**
 * Helper class for reflecting over the type hierarchy of parameterized types where the generic type
 * arguments are reified and/or mapped to the type parameters of the implemented superclasses or
 * interfaces.
 */
public final class ParameterizedClass implements ParameterizedType {
  private final Class<?> rawClass;
  private final Type[] typeArguments;
  private final Type ownerType;

  public static ParameterizedClass of(Type type) {
    Objects.requireNonNull(type);
    if (type instanceof ParameterizedType) {
      return new ParameterizedClass((ParameterizedType) type);
    } else if (type instanceof Class) {
      return new ParameterizedClass((Class<?>) type);
    }
    throw new IllegalArgumentException();
  }

  private ParameterizedClass(Class<?> cls) {
    this.rawClass = cls;
    this.typeArguments = cls.getTypeParameters();
    this.ownerType = cls.getEnclosingClass();
  }

  private ParameterizedClass(ParameterizedType parameterizedType) {
    this(parameterizedType, parameterizedType.getActualTypeArguments());
  }

  private ParameterizedClass(ParameterizedType parameterizedType, Type[] actualTypeArguments) {
    this.rawClass = (Class<?>) parameterizedType.getRawType();
    this.typeArguments = actualTypeArguments;
    this.ownerType = parameterizedType.getOwnerType();
  }

  public ParameterizedClass getParameterizedSuperclass() {
    return withMappedTypeArguments(rawClass.getGenericSuperclass());
  }

  public ParameterizedClass[] getParameterizedInterfaces() {
    Type[] interfaceTypes = rawClass.getGenericInterfaces();
    ParameterizedClass[] parameterizedClasses = new ParameterizedClass[interfaceTypes.length];
    for (int i = 0; i < interfaceTypes.length; i++) {
      parameterizedClasses[i] = withMappedTypeArguments(interfaceTypes[i]);
    }
    return parameterizedClasses;
  }

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

  private ParameterizedClass withMappedTypeArguments(Type superType) {
    if (superType instanceof ParameterizedType) {
      ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
      TypeVariable<?>[] typeParameters = rawClass.getTypeParameters();
      return withMappedTypeArguments(parameterizedSuperType, typeParameters, typeArguments);
    } else if (superType != null) {
      return ParameterizedClass.of(superType);
    }
    return null;
  }

  private static ParameterizedClass withMappedTypeArguments(
      ParameterizedType parameterizedSuperType,
      TypeVariable<?>[] typeParameters,
      Type[] typeArguments) {
    Type[] superTypeArguments = parameterizedSuperType.getActualTypeArguments();

    for (int i = 0; i < superTypeArguments.length; i++) {
      Type superTypeArgument = superTypeArguments[i];
      if (superTypeArgument instanceof TypeVariable) {
        TypeVariable<?> superTypeVariable = (TypeVariable<?>) superTypeArgument;
        superTypeArguments[i] =
            mapTypeVariableToArgument(superTypeVariable, typeParameters, typeArguments);
      }
    }
    return new ParameterizedClass(parameterizedSuperType, superTypeArguments);
  }

  private static Type mapTypeVariableToArgument(
      TypeVariable<?> superTypeVariable, TypeVariable<?>[] typeParameters, Type[] typeArguments) {
    for (int i = 0; i < typeParameters.length; i++) {
      if (equalsTypeVariable(superTypeVariable, typeParameters[i])) {
        return typeArguments[i];
      }
    }
    return superTypeVariable;
  }

  private static boolean equalsTypeVariable(TypeVariable<?> left, TypeVariable<?> right) {
    if (left == right) {
      return true;
    } else if (left == null || right == null) {
      return false;
    }
    return left.getGenericDeclaration().equals(right.getGenericDeclaration())
        && left.getName().equals(right.getName());
  }

  @Override
  public Type[] getActualTypeArguments() {
    return typeArguments.clone();
  }

  @Override
  public Type getRawType() {
    return rawClass;
  }

  @Override
  public Type getOwnerType() {
    return ownerType;
  }

  @Override
  public String getTypeName() {
    StringBuilder sb = new StringBuilder();

    if (ownerType instanceof ParameterizedType) {
      sb.append(ownerType);
    } else if (ownerType != null) {
      sb.append(ownerType.getTypeName()).append("$").append(rawClass.getSimpleName());
    } else {
      sb.append(rawClass.getName());
    }

    if (typeArguments != null && typeArguments.length > 0) {
      sb.append("<");
      for (int i = 0; i < typeArguments.length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(typeArguments[i].getTypeName());
      }
      sb.append(">");
    }

    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof ParameterizedClass) {
      ParameterizedClass other = (ParameterizedClass) o;
      return rawClass.equals(other.rawClass);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return rawClass.hashCode();
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
