/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaHttpClientInstrumentationModule extends InstrumentationModule {
  public AkkaHttpClientInstrumentationModule() {
    super(
        "akka-http",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"akka-http-10.0", "akka-http-10.0-client"}
            : new String[] {"akka-http-10.0", "akka-http-client"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpExtClientInstrumentation(), new PoolMasterActorInstrumentation());
  }
}
