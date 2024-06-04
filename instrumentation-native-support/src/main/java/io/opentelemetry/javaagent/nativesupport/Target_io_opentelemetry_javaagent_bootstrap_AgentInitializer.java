/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.bootstrap.AgentStarter;

import java.io.File;
import java.lang.instrument.Instrumentation;

@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@TargetClass(AgentInitializer.class)
public final class Target_io_opentelemetry_javaagent_bootstrap_AgentInitializer {
  @Alias private static ClassLoader agentClassLoader;

  @Alias private static AgentStarter agentStarter;

  @SuppressWarnings("Unused")
  @Substitute
  public static void initialize(Instrumentation inst, File javaagentFile, boolean fromPremain) {
    if (agentClassLoader != null) {
      return;
    }
    agentClassLoader = ClassLoader.getSystemClassLoader();
    agentStarter = createAgentStarter(agentClassLoader, inst, javaagentFile);
    agentStarter.start();
  }

  @Alias(noSubstitution = true)
  private static native AgentStarter createAgentStarter(
      ClassLoader agentClassLoader, Instrumentation instrumentation, File javaagentFile);
}
