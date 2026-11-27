/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16;

import static java.util.logging.Level.FINE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class MethodAnnotationResolver {

  private static final Logger logger = Logger.getLogger(MethodAnnotationResolver.class.getName());

  private static final ClassValue<ConcurrentMap<Method, ResolvedMethod>> RESOLVED_METHOD_CACHE =
      new ClassValue<ConcurrentMap<Method, ResolvedMethod>>() {
        @Override
        protected ConcurrentMap<Method, ResolvedMethod> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  @Nullable
  static application.io.opentelemetry.instrumentation.annotations.WithSpan findWithSpanOrNull(
      Method method) {
    return resolve(method).withSpan();
  }

  @Nullable
  static <A extends Annotation> A findParameterAnnotationOrNull(
      Method method, int index, Class<A> annotationType) {
    return resolve(method).parameterAnnotation(annotationType, index);
  }

  private static ResolvedMethod resolve(Method method) {
    return RESOLVED_METHOD_CACHE
        .get(method.getDeclaringClass())
        .computeIfAbsent(method, m -> new ResolvedMethod(methodAndOverridden(m)));
  }

  @Nullable
  private static application.io.opentelemetry.instrumentation.annotations.WithSpan
      firstWithSpanOrNull(List<Method> methods) {
    for (Method method : methods) {
      application.io.opentelemetry.instrumentation.annotations.WithSpan withSpan =
          method.getDeclaredAnnotation(
              application.io.opentelemetry.instrumentation.annotations.WithSpan.class);
      if (withSpan != null) {
        return withSpan;
      }
    }
    return null;
  }

  private static List<Method> methodAndOverridden(Method method) {
    List<Method> result = new ArrayList<>();
    result.add(method);
    if (isOverridable(method)) {
      try {
        collectOverridden(method, result);
      } catch (Throwable t) {
        logger.log(FINE, "failed to resolve overridden methods of " + method, t);
      }
    }
    return result;
  }

  private static void collectOverridden(Method method, List<Method> result) {
    Map<TypeVariable<?>, Type> typeArguments = new HashMap<>();
    Deque<Type> queue = new ArrayDeque<>();
    Set<Class<?>> visited = new HashSet<>();
    addSuperTypes(method.getDeclaringClass(), queue);
    while (!queue.isEmpty()) {
      Type type = queue.poll();
      Class<?> rawType = erasure(type, typeArguments);
      if (!visited.add(rawType)) {
        continue;
      }
      bindTypeArguments(type, rawType, typeArguments);
      for (Method candidate : declaredMethods(rawType)) {
        if (overrides(method, candidate, typeArguments)) {
          result.add(candidate);
        }
      }
      addSuperTypes(rawType, queue);
    }
  }

  private static Method[] declaredMethods(Class<?> type) {
    try {
      return type.getDeclaredMethods();
    } catch (Throwable t) {
      logger.log(FINE, "failed to read declared methods of " + type.getName(), t);
      return new Method[0];
    }
  }

  private static void bindTypeArguments(
      Type type, Class<?> rawType, Map<TypeVariable<?>, Type> typeArguments) {
    if (type instanceof ParameterizedType) {
      Type owner = ((ParameterizedType) type).getOwnerType();
      if (owner instanceof ParameterizedType) {
        bindTypeArguments(
            owner, (Class<?>) ((ParameterizedType) owner).getRawType(), typeArguments);
      }
      TypeVariable<?>[] variables = rawType.getTypeParameters();
      Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
      for (int i = 0; i < variables.length && i < arguments.length; i++) {
        if (!variables[i].equals(arguments[i])) {
          typeArguments.put(variables[i], arguments[i]);
        }
      }
    }
  }

  private static void addSuperTypes(Class<?> type, Deque<Type> queue) {
    try {
      Type superClass = type.getGenericSuperclass();
      if (superClass != null) {
        queue.add(superClass);
      }
      Collections.addAll(queue, type.getGenericInterfaces());
    } catch (Throwable t) {
      logger.log(FINE, "failed to resolve generic super types of " + type.getName(), t);
      if (type.getSuperclass() != null) {
        queue.add(type.getSuperclass());
      }
      Collections.addAll(queue, type.getInterfaces());
    }
  }

  private static boolean isOverridable(Method method) {
    int modifiers = method.getModifiers();
    return !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) && !method.isBridge();
  }

  private static boolean overrides(
      Method method, Method candidate, Map<TypeVariable<?>, Type> typeArguments) {
    return isOverridable(candidate)
        && candidate.getName().equals(method.getName())
        && (isVisibleTo(candidate, method.getDeclaringClass())
            || isOverriddenViaSamePackageClass(candidate, method))
        && parametersMatch(method, candidate, typeArguments);
  }

  private static boolean isVisibleTo(Method candidate, Class<?> type) {
    int modifiers = candidate.getModifiers();
    return Modifier.isPublic(modifiers)
        || Modifier.isProtected(modifiers)
        || packageName(candidate.getDeclaringClass()).equals(packageName(type));
  }

  private static boolean isOverriddenViaSamePackageClass(Method candidate, Method method) {
    Class<?> candidateType = candidate.getDeclaringClass();
    for (Class<?> type = method.getDeclaringClass().getSuperclass();
        type != null && type != candidateType;
        type = type.getSuperclass()) {
      if (packageName(type).equals(packageName(candidateType))
          && declaresVisibleOverride(type, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean declaresVisibleOverride(Class<?> type, Method method) {
    for (Method declared : declaredMethods(type)) {
      if (isOverridable(declared)
          && isVisibleTo(declared, method.getDeclaringClass())
          && declared.getName().equals(method.getName())
          && Arrays.equals(declared.getParameterTypes(), method.getParameterTypes())) {
        return true;
      }
    }
    return false;
  }

  private static String packageName(Class<?> type) {
    String name = type.getName();
    int index = name.lastIndexOf('.');
    return index < 0 ? "" : name.substring(0, index);
  }

  private static boolean parametersMatch(
      Method method, Method candidate, Map<TypeVariable<?>, Type> typeArguments) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    Type[] candidateTypes = candidate.getGenericParameterTypes();
    if (parameterTypes.length != candidateTypes.length) {
      return false;
    }
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!parameterTypes[i].equals(erasure(candidateTypes[i], typeArguments))) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> erasure(Type type, Map<TypeVariable<?>, Type> typeArguments) {
    if (type instanceof Class) {
      return (Class<?>) type;
    }
    if (type instanceof ParameterizedType) {
      return erasure(((ParameterizedType) type).getRawType(), typeArguments);
    }
    if (type instanceof GenericArrayType) {
      Class<?> componentType =
          erasure(((GenericArrayType) type).getGenericComponentType(), typeArguments);
      return Array.newInstance(componentType, 0).getClass();
    }
    if (type instanceof TypeVariable) {
      Type argument = typeArguments.get(type);
      return erasure(
          argument != null ? argument : ((TypeVariable<?>) type).getBounds()[0], typeArguments);
    }
    if (type instanceof WildcardType) {
      return erasure(((WildcardType) type).getUpperBounds()[0], typeArguments);
    }
    return Object.class;
  }

  private MethodAnnotationResolver() {}

  private static final class ResolvedMethod {
    private final List<Method> methodAndOverridden;

    private final ConcurrentMap<Class<? extends Annotation>, Annotation[]> parameterAnnotations =
        new ConcurrentHashMap<>();

    @Nullable
    private final application.io.opentelemetry.instrumentation.annotations.WithSpan withSpan;

    private ResolvedMethod(List<Method> methodAndOverridden) {
      this.methodAndOverridden = methodAndOverridden;
      this.withSpan = firstWithSpanOrNull(methodAndOverridden);
    }

    @Nullable
    <A extends Annotation> A parameterAnnotation(Class<A> annotationType, int index) {
      Annotation[] annotations =
          parameterAnnotations.computeIfAbsent(annotationType, this::loadParameterAnnotations);
      return index < annotations.length ? annotationType.cast(annotations[index]) : null;
    }

    private Annotation[] loadParameterAnnotations(Class<? extends Annotation> annotationType) {
      Annotation[] result = new Annotation[methodAndOverridden.get(0).getParameterCount()];
      for (Method candidate : methodAndOverridden) {
        Parameter[] parameters = candidate.getParameters();
        for (int i = 0; i < result.length && i < parameters.length; i++) {
          if (result[i] == null) {
            result[i] = parameters[i].getDeclaredAnnotation(annotationType);
          }
        }
      }
      return result;
    }

    @Nullable
    application.io.opentelemetry.instrumentation.annotations.WithSpan withSpan() {
      return withSpan;
    }
  }
}
