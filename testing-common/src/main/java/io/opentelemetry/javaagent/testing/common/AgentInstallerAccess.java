/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class AgentInstallerAccess {

  private static final MethodHandle resetInstrumentation;

  static {
    try {
      Class<?> agentInstallerClass =
          AgentClassLoaderAccess.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      resetInstrumentation =
          lookup.findStatic(agentInstallerClass, "resetInstrumentation", methodType(void.class));
    } catch (Throwable t) {
      throw new Error("Could not load agent installer.", t);
    }
  }

  public static void resetInstrumentation() {
    try {
      resetInstrumentation.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke resetInstrumentation", t);
    }
  }

  private AgentInstallerAccess() {}
}
