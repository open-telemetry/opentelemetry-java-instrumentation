/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class SpringRmiServerRequest {

  private static final ClassValue<Map<MethodSignature, String>> rpcMethods =
      new ClassValue<Map<MethodSignature, String>>() {
        @Override
        protected Map<MethodSignature, String> computeValue(Class<?> serviceInterface) {
          Map<MethodSignature, String> methods = new HashMap<>();
          for (Method method : serviceInterface.getMethods()) {
            MethodSignature signature =
                new MethodSignature(method.getName(), method.getParameterTypes());
            if (methods.containsKey(signature)) {
              continue;
            }
            String rpcMethod = null;
            try {
              Method resolvedMethod =
                  serviceInterface.getMethod(signature.name, signature.parameterTypes);
              rpcMethod =
                  resolvedMethod.getDeclaringClass().getName() + "/" + resolvedMethod.getName();
            } catch (NoSuchMethodException ignored) {
              // A method returned by getMethods() should always be resolvable by its signature.
            }
            methods.put(signature, rpcMethod);
          }
          return Collections.unmodifiableMap(methods);
        }
      };

  @Nullable private final Class<?> serviceInterface;
  private final MethodSignature methodSignature;

  SpringRmiServerRequest(
      @Nullable Class<?> serviceInterface, String methodName, @Nullable Class<?>[] parameterTypes) {
    this.serviceInterface = serviceInterface;
    this.methodSignature = new MethodSignature(methodName, parameterTypes);
  }

  String rpcMethod() {
    String method =
        serviceInterface == null ? null : rpcMethods.get(serviceInterface).get(methodSignature);
    return method == null ? "_OTHER" : method;
  }

  @Nullable
  String rpcMethodOriginal() {
    if (!rpcMethod().equals("_OTHER")) {
      return null;
    }
    return serviceInterface == null
        ? methodSignature.name
        : serviceInterface.getName() + "/" + methodSignature.name;
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
