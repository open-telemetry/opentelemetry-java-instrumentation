package io.opentelemetry.javaagent.testing.common;

import static java.lang.invoke.MethodType.methodType;

import io.opentelemetry.javaagent.bootstrap.TransformationListener;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class AgentInstallerAccess {

  private static final MethodHandle getInstrumentation;
  private static final MethodHandle installBytebuddyAgent;

  static {
    try {
      Class<?> agentInstallerClass =
          AgentClassLoaderAccess.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      getInstrumentation =
          lookup.findStatic(
              agentInstallerClass, "getInstrumentation", methodType(Instrumentation.class));

      installBytebuddyAgent =
          lookup.findStatic(
              agentInstallerClass,
              "installBytebuddyAgent",
              methodType(
                  ClassFileTransformer.class,
                  Instrumentation.class,
                  boolean.class,
                  TransformationListener[].class));
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

  public static ClassFileTransformer installBytebuddyAgent(
      Instrumentation inst,
      boolean skipAdditionalLibraryMatcher,
      TransformationListener... listeners) {
    try {
      return (ClassFileTransformer)
          installBytebuddyAgent.invoke(inst, skipAdditionalLibraryMatcher, listeners);
    } catch (Throwable t) {
      throw new Error("Could not invoke installBytebuddyAgent", t);
    }
  }

  private AgentInstallerAccess() {}
}
