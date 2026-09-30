/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor.v2_3;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaActorForkJoinInstrumentationModule extends InstrumentationModule {
  public AkkaActorForkJoinInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "akka-actor" : "akka-actor-forkjoin",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"akka-actor-2.3", "akka-actor-2.3-forkjoin"}
            : expandDeprecatedNames(
                "akka-actor-forkjoin|deprecated:akka-actor-fork-join",
                "akka-actor-forkjoin-2.5|deprecated:akka-actor-fork-join-2.5",
                "akka-actor"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AkkaForkJoinPoolInstrumentation(), new AkkaForkJoinTaskInstrumentation());
  }
}
