package io.opentelemetry.javaagent.bootstrap;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class IndyBootstrapDispatcher {

  public static Method bootstrap;
  public static Method logAdviceException;

  private static final MethodHandle VOID_NOOP;

  static {
    try {
      VOID_NOOP = MethodHandles.publicLookup().findStatic(IndyBootstrapDispatcher.class, "voidNoop", MethodType.methodType(void.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static CallSite bootstrap(MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object... args) {
    CallSite callSite = null;
    if (bootstrap != null) {
      try {
        callSite = (CallSite) bootstrap.invoke(null,
            lookup,
            adviceMethodName,
            adviceMethodType,
            args);
      } catch (Exception e) {
        printStackTrace(e);
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
        noopNoArg = MethodHandles.constant(returnType, Array.get(Array.newInstance(returnType, 1), 0));
      }
      MethodHandle noop = MethodHandles.dropArguments(noopNoArg, 0, adviceMethodType.parameterList());
      callSite = new ConstantCallSite(noop);
    }
    return callSite;
  }

  public static void logAdviceException(Throwable exception) {
    try {
      if (logAdviceException != null) {
        logAdviceException.invoke(null, exception);
      } else {
        printStackTrace(exception);
      }
    } catch (Throwable t) {
      try {
        printStackTrace(t);
      } catch (Throwable e) {
        //nothing we can do here, it seems like we can't event print exceptions (e.g. due to OOM or StackOverflow).
      }
    }
  }

  /**
   * Replicates the logic from SystemStandardOutputLogger, as it cannot be directly accessed here.
   * Note that we don't log anything if the security manager is enabled, as we don't want to deal
   * with doPrivileged() here.
   *
   * @param t the throwable to print
   */
  private static void printStackTrace(Throwable t) {
    if (System.getSecurityManager() == null) {
      boolean loggingDisabled = System.getProperty("elastic.apm.system_output_disabled") != null || System.getenv("ELASTIC_APM_SYSTEM_OUTPUT_DISABLED") != null;
      if (!loggingDisabled) {
        t.printStackTrace();
      }
    }
  }

  public static void voidNoop() {
  }
}
