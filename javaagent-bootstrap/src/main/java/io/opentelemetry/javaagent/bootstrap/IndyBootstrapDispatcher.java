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
@SuppressWarnings("unused")
public class IndyBootstrapDispatcher {

  /**
   * Pointer to the actual bootstrapping implementation. This field is initialized by {@link
   * io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyBootstrap}.
   */
  public static MethodHandle bootstrap;

  private static final MethodHandle VOID_NOOP;

  static {
    try {
      VOID_NOOP =
          MethodHandles.publicLookup()
              .findStatic(
                  IndyBootstrapDispatcher.class, "voidNoop", MethodType.methodType(void.class));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private IndyBootstrapDispatcher() {}

  @SuppressWarnings("CatchAndPrintStackTrace")
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
      Class<?> returnType = adviceMethodType.returnType();
      MethodHandle noopNoArg;
      if (returnType == void.class) {
        noopNoArg = VOID_NOOP;
      } else if (!returnType.isPrimitive()) {
        noopNoArg = MethodHandles.constant(returnType, null);
      } else {
        noopNoArg =
            MethodHandles.constant(returnType, Array.get(Array.newInstance(returnType, 1), 0));
      }
      MethodHandle noop =
          MethodHandles.dropArguments(noopNoArg, 0, adviceMethodType.parameterList());
      callSite = new ConstantCallSite(noop);
    }
    return callSite;
  }

  public static void voidNoop() {}
}
