/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra.v2_9;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class FinatraInstrumentationModule extends InstrumentationModule {
  public FinatraInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "finatra-2.9" : "finatra",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"finatra"}
            : new String[] {"finatra-2.9"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new FinatraRouteInstrumentation(),
        new FinatraRouteBuilderInstrumentation(),
        new FinatraExceptionManagerInstrumentation());
  }
}
