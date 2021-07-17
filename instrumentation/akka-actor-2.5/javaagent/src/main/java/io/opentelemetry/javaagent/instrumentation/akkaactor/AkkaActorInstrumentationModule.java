/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaActorInstrumentationModule extends InstrumentationModule {
  public AkkaActorInstrumentationModule() {
    super("akka-actor", "akka-actor-2.5");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AkkaDispatcherInstrumentation(),
        new AkkaActorCellInstrumentation(),
        new AkkaDefaultSystemMessageQueueInstrumentation());
  }
}
