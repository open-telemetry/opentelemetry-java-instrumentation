/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.javaagent.bootstrap.jaxrs.ClassHierarchyIterable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public abstract class HandlerData {

  protected final Class<?> target;
  protected final Method method;

  protected HandlerData(Class<?> target, Method method) {
    this.target = target;
    this.method = method;
  }

  public Class<?> codeClass() {
    return target;
  }

  public String methodName() {
    return method.getName();
  }

  public String getServerSpanName() {
    String httpMethod = null;
    String methodPath = null;
    String classPath = findClassPath(target);
    for (Class<?> currentClass : new ClassHierarchyIterable(target)) {
      Method currentMethod;
      if (currentClass.equals(target)) {
        currentMethod = method;
      } else {
        currentMethod = findMatchingMethod(method, currentClass.getDeclaredMethods());
      }

      if (currentMethod != null) {
        if (httpMethod == null) {
          httpMethod = locateHttpMethod(currentMethod);
        }
        if (methodPath == null) {
          methodPath = findMethodPath(currentMethod);
        }

        if (httpMethod != null && methodPath != null) {
          break;
        }
      }
    }
    return buildSpanName(classPath, methodPath);
  }

  protected abstract Class<? extends Annotation> getHttpMethodAnnotation();

  private String locateHttpMethod(Method method) {
    String httpMethod = null;
    for (Annotation ann : method.getDeclaredAnnotations()) {
      if (ann.annotationType().getAnnotation(getHttpMethodAnnotation()) != null) {
        httpMethod = ann.annotationType().getSimpleName();
      }
    }
    return httpMethod;
  }

  private String findMethodPath(Method method) {
    Supplier<String> pathSupplier = getPathAnnotation(method);
    return pathSupplier != null ? pathSupplier.get() : null;
  }

  protected abstract Supplier<String> getPathAnnotation(AnnotatedElement annotated);

  private String findClassPath(Class<?> target) {
    for (Class<?> currentClass : new ClassHierarchyIterable(target)) {
      Supplier<String> pathSupplier = getPathAnnotation(currentClass);
      if (pathSupplier != null) {
        // Annotation overridden, no need to continue.
        return pathSupplier.get();
      }
    }

    return null;
  }

  private static Method findMatchingMethod(Method baseMethod, Method[] methods) {
    nextMethod:
    for (Method method : methods) {
      if (!baseMethod.getReturnType().equals(method.getReturnType())) {
        continue;
      }

      if (!baseMethod.getName().equals(method.getName())) {
        continue;
      }

      Class<?>[] baseParameterTypes = baseMethod.getParameterTypes();
      Class<?>[] parameterTypes = method.getParameterTypes();
      if (baseParameterTypes.length != parameterTypes.length) {
        continue;
      }
      for (int i = 0; i < baseParameterTypes.length; i++) {
        if (!baseParameterTypes[i].equals(parameterTypes[i])) {
          continue nextMethod;
        }
      }
      return method;
    }
    return null;
  }

  private static String buildSpanName(String classPath, String methodPath) {
    StringBuilder spanNameBuilder = new StringBuilder();
    boolean skipSlash = false;
    if (classPath != null) {
      if (!classPath.startsWith("/")) {
        spanNameBuilder.append("/");
      }
      spanNameBuilder.append(classPath);
      skipSlash = classPath.endsWith("/") || classPath.isEmpty();
    }

    if (methodPath != null) {
      if (skipSlash) {
        if (methodPath.startsWith("/")) {
          methodPath = methodPath.length() == 1 ? "" : methodPath.substring(1);
        }
      } else if (!methodPath.startsWith("/")) {
        spanNameBuilder.append("/");
      }
      spanNameBuilder.append(methodPath);
    }

    return spanNameBuilder.toString().trim();
  }
}
