/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentLoadedChecker {

  public static void main(String[] args) throws Exception {
    // Empty class loader that delegates to bootstrap
    try (URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null)) {
      Class<?> agentClass =
          emptyClassLoader.loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");

      assertThat(agentClass.getClassLoader()).isNull();
      assertThat(agentClass.getMethod("isAgentStarted", boolean.class).invoke(null, true))
          .isEqualTo(true);
      assertThat(
              emptyClassLoader
                  .loadClass("io.opentelemetry.javaagent.bootstrap.InstrumentationHolder")
                  .getMethod("getInstrumentation")
                  .invoke(null))
          .isInstanceOf(Instrumentation.class);
      assertThat(
              emptyClassLoader
                  .loadClass("io.opentelemetry.javaagent.bootstrap.JavaagentFileHolder")
                  .getMethod("getJavaagentFile")
                  .invoke(null))
          .isEqualTo(new File(args[0]));
    }
  }

  private AgentLoadedChecker() {}
}
