/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HelperInjectorAccess {

  private static final MethodHandle helperInjectorConstructor;
  private static final MethodHandle transform;

  static {
    try {
      Class<?> helperInjectorClass =
          AgentClassLoaderAccess.loadClass("io.opentelemetry.javaagent.tooling.HelperInjector");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      helperInjectorConstructor =
          lookup.findConstructor(
              helperInjectorClass, methodType(void.class, String.class, List.class, List.class));
      transform =
          lookup.unreflect(
              Arrays.stream(helperInjectorClass.getDeclaredMethods())
                  .filter(method -> method.getName().equals("transform"))
                  .findFirst()
                  .get());
    } catch (Throwable t) {
      throw new Error("Could not access HelperInjector through reflection");
    }
  }

  public static void injectResources(ClassLoader classLoader, String... resources) {
    try {
      Object injector =
          helperInjectorConstructor.invoke(
              "test", Collections.emptyList(), Arrays.asList(resources));
      transform.invoke(injector, null, null, classLoader, null);
    } catch (Throwable t) {
      throw new Error("Could not invoke helper injection through reflection.");
    }
  }

  private HelperInjectorAccess() {}
}
