/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

/**
 * Contains the bootstrap method for initializing invokedynamic callsites which are added via agent
 * instrumentation.
 */
public class IndyBootstrapDispatcher {

  private static volatile MethodHandle bootstrap;

  private IndyBootstrapDispatcher() {}

  /**
   * Initialized the invokedynamic bootstrapping method to which this class will delegate.
   *
   * @param bootstrapMethod the method to delegate to. Must have the same type as {@link
   *     #bootstrap}.
   */
  public static void init(MethodHandle bootstrapMethod) {
    bootstrap = bootstrapMethod;
  }

  public static CallSite bootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object... args) {
    CallSite callSite = null;
    if (bootstrap != null) {
      try {
        callSite = (CallSite) bootstrap.invoke(lookup, adviceMethodName, adviceMethodType, args);
      } catch (Throwable e) {
        ExceptionLogger.logSuppressedError("Error bootstrapping indy instruction", e);
      }
    }
    if (callSite == null) {
      // The MethodHandle pointing to the Advice could not be created for some reason,
      // fallback to a Noop MethodHandle to not crash the application
      MethodHandle noop = generateNoopMethodHandle(adviceMethodType);
      callSite = new ConstantCallSite(noop);
    }
    return callSite;
  }

  public static MethodHandle generateNoopMethodHandle(MethodType methodType) {
    Class<?> returnType = methodType.returnType();
    MethodHandle noopNoArg;
    if (returnType == void.class) {
      noopNoArg =
          MethodHandles.constant(Void.class, null).asType(MethodType.methodType(void.class));
    } else {
      noopNoArg = MethodHandles.constant(returnType, getDefaultValue(returnType));
    }
    return MethodHandles.dropArguments(noopNoArg, 0, methodType.parameterList());
  }

  private static Object getDefaultValue(Class<?> classOrPrimitive) {
    if (classOrPrimitive.isPrimitive()) {
      // arrays of primitives are initialized with the correct primitive default value (e.g. 0 for
      // int.class)
      // we use this fact to generate the correct default value reflectively
      return Array.get(Array.newInstance(classOrPrimitive, 1), 0);
    } else {
      return null; // null is the default value for reference types
    }
  }
}
