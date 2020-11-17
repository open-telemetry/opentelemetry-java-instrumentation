package io.opentelemetry.javaagent.testing.common;

import static java.lang.invoke.MethodType.methodType;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class AgentInstallerAccess {

  private static final MethodHandle getInstrumentation;
  static {
    try {
      Class<?> agentInstallerClass =
          AgentClassLoaderAccess.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      getInstrumentation =
          lookup.findStatic(
              agentInstallerClass, "getInstrumentation", methodType(Instrumentation.class));
    } catch (Throwable t) {
      throw new Error("Could not load agent installer.", t);
    }
  }

  public static Instrumentation getInstrumentation() {
    try {
      return (Instrumentation) getInstrumentation.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke getInstrumentation", t);
    }
  }

  private AgentInstallerAccess() {}
}
