/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import io.opentelemetry.javaagent.instrumentation.api.SafeServiceLoader;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(AgentExtension.class)
public class InstrumentationLoader implements AgentExtension {
  private static final Logger log = LoggerFactory.getLogger(InstrumentationLoader.class);

  private final InstrumentationModuleInstaller instrumentationModuleInstaller =
      new InstrumentationModuleInstaller();

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder) {
    List<InstrumentationModule> instrumentationModules =
        SafeServiceLoader.load(InstrumentationModule.class).stream()
            .sorted(Comparator.comparingInt(InstrumentationModule::order))
            .collect(Collectors.toList());

    int numberOfLoadedModules = 0;
    for (InstrumentationModule instrumentationModule : instrumentationModules) {
      log.debug(
          "Loading instrumentation {} [class {}]",
          instrumentationModule.instrumentationName(),
          instrumentationModule.getClass().getName());
      try {
        agentBuilder = instrumentationModuleInstaller.install(instrumentationModule, agentBuilder);
        numberOfLoadedModules++;
      } catch (Exception | LinkageError e) {
        log.error(
            "Unable to load instrumentation {} [class {}]",
            instrumentationModule.instrumentationName(),
            instrumentationModule.getClass().getName(),
            e);
      }
    }
    log.debug("Installed {} instrumenter(s)", numberOfLoadedModules);

    return agentBuilder;
  }

  @Override
  public String extensionName() {
    return "instrumentation-loader";
  }
}
