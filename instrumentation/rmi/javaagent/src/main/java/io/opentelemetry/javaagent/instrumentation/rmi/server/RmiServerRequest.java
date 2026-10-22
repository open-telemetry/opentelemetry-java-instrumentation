/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.rmi.Remote;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class RmiServerRequest {

  private static final ClassValue<Map<Method, String>> rpcMethodNames =
      new ClassValue<Map<Method, String>>() {
        @Override
        protected Map<Method, String> computeValue(Class<?> type) {
          Set<Method> declarations = new HashSet<>();
          for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
              if (Remote.class.isAssignableFrom(iface)) {
                for (Method method : iface.getMethods()) {
                  if (!Modifier.isStatic(method.getModifiers())) {
                    declarations.add(method);
                  }
                }
              }
            }
          }
          Map<Method, String> result = new HashMap<>();
          for (Method implementation : type.getMethods()) {
            String match = null;
            for (Method declaration : declarations) {
              if (declaration.getName().equals(implementation.getName())
                  && Arrays.equals(
                      declaration.getParameterTypes(), implementation.getParameterTypes())
                  && declaration.getReturnType().isAssignableFrom(implementation.getReturnType())) {
                String candidate =
                    declaration.getDeclaringClass().getName() + "/" + declaration.getName();
                if (match != null && !match.equals(candidate)) {
                  // Multiple declarations agree only when they emit the same logical RPC name.
                  match = null;
                  break;
                }
                match = candidate;
              }
            }
            if (match != null) {
              result.put(implementation, match);
            }
          }
          return result;
        }
      };

  private final Class<?> serverClass;
  private final Method method;

  RmiServerRequest(Class<?> serverClass, Method method) {
    this.serverClass = serverClass;
    this.method = method;
  }

  Method method() {
    return method;
  }

  @Nullable
  String rpcMethodName() {
    return rpcMethodNames.get(serverClass).get(method);
  }
}
