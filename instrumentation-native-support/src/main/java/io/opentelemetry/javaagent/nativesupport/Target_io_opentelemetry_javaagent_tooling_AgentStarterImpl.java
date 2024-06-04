/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.opentelemetry.javaagent.tooling.AgentStarterImpl;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;

@TargetClass(AgentStarterImpl.class)
public final class Target_io_opentelemetry_javaagent_tooling_AgentStarterImpl {

  @SuppressWarnings({"MethodCanBeStatic", "UnusedVariable", "Unused"})
  @Substitute
  private ClassLoader createExtensionClassLoader(
      ClassLoader agentClassLoader, EarlyInitAgentConfig earlyConfig) {
    return ClassLoader.getSystemClassLoader();
  }
}
