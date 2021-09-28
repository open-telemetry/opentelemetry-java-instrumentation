/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptyMap;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to access {@code getMuzzleReferences} method from the {@link
 * InstrumentationModule} class. That method is not visible in the source code and instead is
 * generated automatically during compilation by the {@code
 * io.opentelemetry.instrumentation.javaagent-codegen} Gradle plugin.
 */
class MuzzleReferencesAccessor {

  private static final Logger logger = LoggerFactory.getLogger(MuzzleReferencesAccessor.class);

  private static final ClassValue<MethodHandle> getMuzzleReferences =
      new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
          MethodHandles.Lookup lookup = MethodHandles.publicLookup();
          MethodHandle handle;
          try {
            // This method is generated automatically during compilation by
            // the io.opentelemetry.instrumentation.javaagent-codegen Gradle plugin.
            handle =
                lookup.findVirtual(type, "getMuzzleReferences", MethodType.methodType(Map.class));
          } catch (NoSuchMethodException | IllegalAccessException e) {
            handle = null;
          }
          return handle;
        }
      };

  /**
   * Returns references to helper and library classes used in the given module's type
   * instrumentation advices, grouped by {@link ClassRef#getClassName()}.
   */
  @SuppressWarnings("unchecked")
  static Map<String, ClassRef> getFor(InstrumentationModule instrumentationModule) {
    if (instrumentationModule instanceof InstrumentationModuleMuzzle) {
      return ((InstrumentationModuleMuzzle) instrumentationModule).getMuzzleReferences();
    }

    // Older classes created and compiled outside of this repo may not yet have the interface above.
    MethodHandle methodHandle = getMuzzleReferences.get(instrumentationModule.getClass());
    if (methodHandle != null) {
      logger.warn(
          "{} is compiled with old version of Muzzle and must be recompiled against newer version of OpenTelemetry Java Instrumentation APIs",
          instrumentationModule);
    }
    return emptyMap();
  }
}
