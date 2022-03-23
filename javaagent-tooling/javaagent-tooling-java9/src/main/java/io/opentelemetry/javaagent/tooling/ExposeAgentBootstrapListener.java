/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * Ensures that transformed classes can read agent classes in bootstrap class loader and injected
 * classes in unnamed module of their class loader.
 */
public class ExposeAgentBootstrapListener extends AgentBuilder.Listener.Adapter {
  private static final Logger logger =
      Logger.getLogger(ExposeAgentBootstrapListener.class.getName());

  // unnamed module in bootstrap class loader
  private static final JavaModule agentBootstrapModule =
      JavaModule.of(AgentClassLoader.class.getModule());

  private final Instrumentation instrumentation;

  public ExposeAgentBootstrapListener(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  @Override
  public void onTransformation(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule javaModule,
      boolean b,
      DynamicType dynamicType) {
    // expose agent classes in unnamed module of bootstrap class loader
    exposeModule(javaModule, agentBootstrapModule);
    if (classLoader != null) {
      // expose classes in unnamed module of current class loader
      // this is needed so that advice code can access injected helper classes
      exposeModule(javaModule, JavaModule.of(classLoader.getUnnamedModule()));
    }
  }

  private void exposeModule(JavaModule fromModule, JavaModule targetModule) {
    if (fromModule != JavaModule.UNSUPPORTED
        && fromModule.isNamed()
        && !fromModule.canRead(targetModule)) {

      logger.log(
          Level.FINE,
          "Adding module read from {0} to {1}",
          new Object[] {fromModule, targetModule});

      ClassInjector.UsingInstrumentation.redefineModule(
          instrumentation,
          fromModule,
          Collections.singleton(targetModule),
          Collections.emptyMap(),
          Collections.emptyMap(),
          Collections.emptySet(),
          Collections.emptyMap());
    }
  }
}
