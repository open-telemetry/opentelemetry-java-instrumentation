/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.instrument.Instrumentation;

public class AgentLoadedChecker {

  public static void main(String[] args) throws Exception {
    Class<?> agentClass =
        Class.forName("io.opentelemetry.javaagent.bootstrap.AgentInitializer", false, null);

    assertThat(agentClass.getClassLoader()).isNull();
    assertThat(agentClass.getMethod("isAgentStarted", boolean.class).invoke(null, true))
        .isEqualTo(true);
    assertThat(
            Class.forName("io.opentelemetry.javaagent.bootstrap.InstrumentationHolder", false, null)
                .getMethod("getInstrumentation")
                .invoke(null))
        .isInstanceOf(Instrumentation.class);
    assertThat(
            Class.forName("io.opentelemetry.javaagent.bootstrap.JavaagentFileHolder", false, null)
                .getMethod("getJavaagentFile")
                .invoke(null))
        .isEqualTo(new File(args[0]));
  }

  private AgentLoadedChecker() {}
}
