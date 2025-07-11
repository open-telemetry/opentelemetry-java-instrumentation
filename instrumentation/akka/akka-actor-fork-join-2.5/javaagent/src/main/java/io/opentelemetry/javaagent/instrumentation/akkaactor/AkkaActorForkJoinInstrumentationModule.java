/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaActorForkJoinInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AkkaActorForkJoinInstrumentationModule() {
    super("akka-actor-fork-join", "akka-actor-fork-join-2.5", "akka-actor");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AkkaForkJoinPoolInstrumentation(), new AkkaForkJoinTaskInstrumentation());
  }
}
