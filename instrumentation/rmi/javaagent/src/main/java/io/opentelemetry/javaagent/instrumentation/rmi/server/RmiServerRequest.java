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

  private static final ClassValue<Map<Method, Method>> remoteMethods =
      new ClassValue<Map<Method, Method>>() {
        @Override
        protected Map<Method, Method> computeValue(Class<?> type) {
          Set<Method> declarations = new HashSet<>();
          // Match UnicastServerRef's export traversal, including interfaces inherited by a class
          // and declarations inherited by a remote interface.
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
          Map<Method, Method> result = new HashMap<>();
          for (Method implementation : type.getMethods()) {
            Method match = null;
            for (Method declaration : declarations) {
              if (declaration.getName().equals(implementation.getName())
                  && Arrays.equals(
                      declaration.getParameterTypes(), implementation.getParameterTypes())
                  && declaration.getReturnType().isAssignableFrom(implementation.getReturnType())) {
                if (match != null) {
                  // RMI hashes the name and full descriptor, not the declaring interface. Export
                  // accepts duplicate declarations and overwrites its hash entry. The invoked
                  // implementation cannot identify the caller's interface in this case. Do not
                  // guess an identity. Covariant bridge calls can also reach the same advised body.
                  match = null;
                  break;
                }
                match = declaration;
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
  Method remoteMethod() {
    return remoteMethods.get(serverClass).get(method);
  }
}
