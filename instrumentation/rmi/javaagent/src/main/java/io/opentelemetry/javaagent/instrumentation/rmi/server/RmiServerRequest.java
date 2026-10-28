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

/**
 * Advice observes the implementation method, but its logical RPC name comes from a remote-interface
 * declaration. Usually one declaration matches. Multiple matches can come from different interfaces
 * exposing the same signature or a covariant override generating a bridge on the same interface.
 * The name is available only when all matching declarations agree on the interface/method name.
 */
final class RmiServerRequest {

  private static final ClassValue<Map<Method, String>> rpcMethodNames =
      new ClassValue<Map<Method, String>>() {
        @Override
        protected Map<Method, String> computeValue(Class<?> serverClass) {
          Set<Method> remoteMethods = findRemoteMethods(serverClass);
          Map<Method, String> names = new HashMap<>();
          for (Method implementation : serverClass.getMethods()) {
            String name = resolveRpcMethodName(implementation, remoteMethods);
            if (name != null) {
              names.put(implementation, name);
            }
          }
          return names;
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

  private static Set<Method> findRemoteMethods(Class<?> serverClass) {
    Set<Method> remoteMethods = new HashSet<>();
    for (Class<?> current = serverClass; current != null; current = current.getSuperclass()) {
      for (Class<?> iface : current.getInterfaces()) {
        if (!Remote.class.isAssignableFrom(iface)) {
          continue;
        }
        // getMethods() includes inherited interface declarations.
        for (Method method : iface.getMethods()) {
          if (!Modifier.isStatic(method.getModifiers())) {
            remoteMethods.add(method);
          }
        }
      }
    }
    return remoteMethods;
  }

  @Nullable
  private static String resolveRpcMethodName(Method implementation, Set<Method> remoteMethods) {
    String name = null;
    for (Method declaration : remoteMethods) {
      if (declaration.getName().equals(implementation.getName())
          && Arrays.equals(declaration.getParameterTypes(), implementation.getParameterTypes())
          && declaration.getReturnType().isAssignableFrom(implementation.getReturnType())) {
        String candidate = declaration.getDeclaringClass().getName() + "/" + declaration.getName();
        // A covariant bridge and its declared method can agree on the same logical RPC name.
        if (name != null && !name.equals(candidate)) {
          return null;
        }
        name = candidate;
      }
    }
    return name;
  }
}
