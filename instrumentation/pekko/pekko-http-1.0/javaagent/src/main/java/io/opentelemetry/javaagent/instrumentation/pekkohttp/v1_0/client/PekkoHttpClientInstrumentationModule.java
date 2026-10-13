/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PekkoHttpClientInstrumentationModule extends InstrumentationModule {
  public PekkoHttpClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "pekko-http-1.0" : "pekko-http",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"pekko-http-client", "pekko-http", "pekko"}
            : new String[] {"pekko-http-1.0", "pekko-http-client"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpExtClientInstrumentation(), new PoolMasterActorInstrumentation());
  }
}
