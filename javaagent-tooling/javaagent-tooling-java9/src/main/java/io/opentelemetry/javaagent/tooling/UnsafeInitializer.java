/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class UnsafeInitializer {
  private static final Logger logger = Logger.getLogger(UnsafeInitializer.class.getName());

  static void initialize(Instrumentation instrumentation, ClassLoader classLoader) {
    initialize(instrumentation, classLoader, true);
  }

  // visible for testing
  static void initialize(
      Instrumentation instrumentation, ClassLoader classLoader, boolean testUnsafePresent) {
    Class<?> unsafeClass;
    try {
      unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
    } catch (ClassNotFoundException exception) {
      return;
    }

    Map<String, Set<Module>> exports = new HashMap<>();
    // expose jdk.internal.misc.Unsafe to our agent
    // this is used to generate our replacement sun.misc.Unsafe and also by grpc/netty to call
    // jdk.internal.misc.Unsafe.allocateUninitializedArray
    exports.put(
        unsafeClass.getPackage().getName(),
        Collections.singleton(UnsafeInitializer.class.getModule()));
    instrumentation.redefineModule(
        unsafeClass.getModule(),
        Collections.emptySet(),
        exports,
        Collections.emptyMap(),
        Collections.emptySet(),
        Collections.emptyMap());

    if (testUnsafePresent && hasSunMiscUnsafe()) {
      return;
    }
    if (!(classLoader instanceof AgentClassLoader)) {
      // some tests don't pass AgentClassLoader, ignore them
      return;
    }

    try {
      SunMiscUnsafeGenerator.generateUnsafe(unsafeClass, (AgentClassLoader) classLoader);
    } catch (Throwable throwable) {
      logger.log(WARNING, "Unsafe generation failed", throwable);
    }
  }

  private static boolean hasSunMiscUnsafe() {
    try {
      Class.forName("sun.misc.Unsafe");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
