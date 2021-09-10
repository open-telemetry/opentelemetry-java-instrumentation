/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptyMap;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.muzzle.ClassRef;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * This class is used to access {@code getMuzzleReferences} method from the {@link
 * InstrumentationModule} class. That method is not visible in the source code and instead is
 * generated automatically during compilation by the {@code
 * io.opentelemetry.instrumentation.javaagent-codegen} Gradle plugin.
 */
class MuzzleReferencesAccessor {
  private static final MethodHandle getMuzzleReferences;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    MethodHandle handle;
    try {
      // This method is generated automatically during compilation by
      // the io.opentelemetry.instrumentation.javaagent-codegen Gradle plugin.
      //noinspection JavaLangInvokeHandleSignature
      handle =
          lookup.findVirtual(
              InstrumentationModule.class, "getMuzzleReferences", MethodType.methodType(Map.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      handle = null;
    }
    getMuzzleReferences = handle;
  }

  /**
   * Returns references to helper and library classes used in the given module's type
   * instrumentation advices, grouped by {@link ClassRef#getClassName()}.
   */
  static Map<String, ClassRef> getFor(InstrumentationModule instrumentationModule) {
    Map<String, ClassRef> muzzleReferences = emptyMap();
    if (getMuzzleReferences != null) {
      try {
        //noinspection unchecked
        muzzleReferences =
            (Map<String, ClassRef>) getMuzzleReferences.invoke(instrumentationModule);
      } catch (Throwable ignored) {
      }
    }
    return muzzleReferences;
  }
}
