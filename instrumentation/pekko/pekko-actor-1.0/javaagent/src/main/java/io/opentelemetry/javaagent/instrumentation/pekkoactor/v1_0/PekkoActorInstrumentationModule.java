/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PekkoActorInstrumentationModule extends InstrumentationModule {
  public PekkoActorInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "pekko-actor-1.0" : "pekko-actor",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"pekko-actor", "pekko"}
            : new String[] {"pekko-actor-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PekkoDispatcherInstrumentation(),
        new PekkoBatchingExecutorInstrumentation(),
        new PekkoActorCellInstrumentation(),
        new PekkoDefaultSystemMessageQueueInstrumentation(),
        new PekkoScheduleInstrumentation());
  }
}
