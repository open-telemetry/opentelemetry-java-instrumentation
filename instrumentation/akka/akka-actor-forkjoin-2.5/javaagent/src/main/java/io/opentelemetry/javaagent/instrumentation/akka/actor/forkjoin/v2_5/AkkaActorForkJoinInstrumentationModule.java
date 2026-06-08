/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akka.actor.forkjoin.v2_5;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaActorForkJoinInstrumentationModule extends InstrumentationModule {
  public AkkaActorForkJoinInstrumentationModule() {
    super(
        "akka-actor-forkjoin",
        expandDeprecatedNames(
            "akka-actor-forkjoin|deprecated:akka-actor-fork-join",
            "akka-actor-forkjoin-2.5|deprecated:akka-actor-fork-join-2.5",
            "akka-actor"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AkkaForkJoinPoolInstrumentation(), new AkkaForkJoinTaskInstrumentation());
  }
}
