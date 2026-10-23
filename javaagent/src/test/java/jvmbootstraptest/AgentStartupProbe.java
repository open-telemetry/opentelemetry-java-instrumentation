/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

/** Observes startup only after the JVM has invoked the real agent's premain. */
@SuppressWarnings("SystemOut")
public class AgentStartupProbe {

  public static void main(String[] args) throws Exception {
    String initializerName = "io.opentelemetry.javaagent.bootstrap.AgentInitializer";
    ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
    Class<?> initializer = systemLoader.loadClass(initializerName);
    Class<?> bootstrapInitializer = Class.forName(initializerName, false, null);

    System.out.println("runtime=" + System.getProperty("java.runtime.version"));
    System.out.println("system-loader=" + systemLoader.getClass().getName());
    System.out.println(
        "premain-loader="
            + systemLoader
                .loadClass("io.opentelemetry.javaagent.OpenTelemetryAgent")
                .getClassLoader()
                .getClass()
                .getName());
    System.out.println(
        "initializer-loader="
            + (initializer.getClassLoader() == null
                ? "bootstrap"
                : initializer.getClassLoader().getClass().getName()));
    System.out.println(
        "agent-started="
            + initializer.getMethod("isAgentStarted", boolean.class).invoke(null, true));
    System.out.println("same-bootstrap-initializer=" + (initializer == bootstrapInitializer));
    System.out.println("application-main-completed");
  }

  private AgentStartupProbe() {}
}
