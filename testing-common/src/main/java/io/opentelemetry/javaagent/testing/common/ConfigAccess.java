/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;

public class ConfigAccess {

  private static final MethodHandle internalGetConfig;
  private static final MethodHandle internalSetConfig;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Class<?> configClass =
          Class.forName("io.opentelemetry.javaagent.shaded.instrumentation.api.config.Config");
      Method internalGetConfigMethod = configClass.getDeclaredMethod("internalGetConfig");
      internalGetConfigMethod.setAccessible(true);
      internalGetConfig = lookup.unreflect(internalGetConfigMethod);

      Method internalSetConfigMethod =
          configClass.getDeclaredMethod("internalSetConfig", Map.class);
      internalSetConfigMethod.setAccessible(true);
      internalSetConfig = lookup.unreflect(internalSetConfigMethod);
    } catch (Throwable t) {
      throw new Error("Could not initialize reflection for Config.", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, String> getConfig() {
    try {
      return (Map<String, String>) internalGetConfig.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke internalGetConfig", t);
    }
  }

  public static void setConfig(Map<String, String> properties) {
    try {
      internalSetConfig.invokeExact(properties);
    } catch (Throwable t) {
      throw new Error("Could not invoke internalSetConfig", t);
    }
  }
}
