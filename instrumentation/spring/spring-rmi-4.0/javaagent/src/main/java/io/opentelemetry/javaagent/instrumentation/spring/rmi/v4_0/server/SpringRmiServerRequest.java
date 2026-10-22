/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

public final class SpringRmiServerRequest {

  private static final ClassValue<ConcurrentMap<MethodSignature, MethodLookup>> remoteMethods =
      new ClassValue<ConcurrentMap<MethodSignature, MethodLookup>>() {
        @Override
        protected ConcurrentMap<MethodSignature, MethodLookup> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  private final ClassAndMethod legacyRequest;
  @Nullable private final Class<?> serviceInterface;
  private final MethodSignature methodSignature;

  SpringRmiServerRequest(
      ClassAndMethod legacyRequest,
      @Nullable Class<?> serviceInterface,
      String methodName,
      @Nullable Class<?>[] parameterTypes) {
    this.legacyRequest = legacyRequest;
    this.serviceInterface = serviceInterface;
    this.methodSignature = new MethodSignature(methodName, parameterTypes);
  }

  ClassAndMethod legacyRequest() {
    return legacyRequest;
  }

  @Nullable
  String rpcMethod() {
    if (serviceInterface == null) {
      return null;
    }
    MethodLookup lookup =
        remoteMethods
            .get(serviceInterface)
            .computeIfAbsent(methodSignature, signature -> findMethod(serviceInterface, signature));
    Method method = lookup.method;
    return method == null ? null : method.getDeclaringClass().getName() + "/" + method.getName();
  }

  private static MethodLookup findMethod(Class<?> serviceInterface, MethodSignature signature) {
    try {
      return new MethodLookup(serviceInterface.getMethod(signature.name, signature.parameterTypes));
    } catch (NoSuchMethodException e) {
      return new MethodLookup(null);
    }
  }

  private static final class MethodLookup {
    @Nullable private final Method method;

    private MethodLookup(@Nullable Method method) {
      this.method = method;
    }
  }

  private static final class MethodSignature {
    private final String name;
    private final Class<?>[] parameterTypes;

    private MethodSignature(String name, @Nullable Class<?>[] parameterTypes) {
      this.name = name;
      this.parameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes.clone();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof MethodSignature)) {
        return false;
      }
      MethodSignature that = (MethodSignature) other;
      return name.equals(that.name) && Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
      return 31 * name.hashCode() + Arrays.hashCode(parameterTypes);
    }
  }
}
