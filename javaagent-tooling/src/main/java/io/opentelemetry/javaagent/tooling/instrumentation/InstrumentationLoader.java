/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static io.opentelemetry.javaagent.tooling.SafeServiceLoader.loadOrdered;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import java.util.logging.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(AgentExtension.class)
public class InstrumentationLoader implements AgentExtension {
  private static final Logger logger = Logger.getLogger(InstrumentationLoader.class.getName());

  private final InstrumentationModuleInstaller instrumentationModuleInstaller =
      new InstrumentationModuleInstaller(InstrumentationHolder.getInstrumentation());

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder) {
    int numberOfLoadedModules = 0;
    for (InstrumentationModule instrumentationModule : loadOrdered(InstrumentationModule.class)) {
      logger.log(
          FINE,
          "Loading instrumentation {0} [class {1}]",
          new Object[] {
            instrumentationModule.instrumentationName(), instrumentationModule.getClass().getName()
          });
      try {
        agentBuilder = instrumentationModuleInstaller.install(instrumentationModule, agentBuilder);
        numberOfLoadedModules++;
      } catch (Exception | LinkageError e) {
        logger.log(
            SEVERE,
            "Unable to load instrumentation "
                + instrumentationModule.instrumentationName()
                + " [class "
                + instrumentationModule.getClass().getName()
                + "]",
            e);
      }
    }
    logger.log(FINE, "Installed {0} instrumenter(s)", numberOfLoadedModules);

    return agentBuilder;
  }

  @Override
  public String extensionName() {
    return "instrumentation-loader";
  }
}
