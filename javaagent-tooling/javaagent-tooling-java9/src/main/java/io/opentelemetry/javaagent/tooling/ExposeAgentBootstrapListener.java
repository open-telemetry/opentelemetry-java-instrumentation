/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ensures that transformed classes can read agent classes in bootstrap class loader. */
public class ExposeAgentBootstrapListener extends AgentBuilder.Listener.Adapter {
  private static final Logger logger = LoggerFactory.getLogger(ExposeAgentBootstrapListener.class);
  // unnamed module in bootstrap class loader
  private static final JavaModule agentBootstrapModule =
      JavaModule.of(AgentClassLoader.class.getModule());

  private final Instrumentation instrumentation;

  public ExposeAgentBootstrapListener(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public void onTransformation(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule javaModule,
      boolean b,
      DynamicType dynamicType) {
    if (JavaModule.isSupported()
        && javaModule != JavaModule.UNSUPPORTED
        && javaModule.isNamed()
        && !javaModule.canRead(agentBootstrapModule)) {
      logger.debug("Adding module read from {} to {}", javaModule, agentBootstrapModule);
      ClassInjector.UsingInstrumentation.redefineModule(
          instrumentation,
          javaModule,
          Collections.singleton(agentBootstrapModule),
          Collections.emptyMap(),
          Collections.emptyMap(),
          Collections.emptySet(),
          Collections.emptyMap());
    }
  }
}
