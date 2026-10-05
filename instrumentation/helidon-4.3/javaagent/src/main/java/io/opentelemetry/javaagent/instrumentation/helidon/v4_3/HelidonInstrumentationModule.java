/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon.v4_3;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HelidonInstrumentationModule extends InstrumentationModule {
  public HelidonInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "helidon-4.3" : "helidon",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"helidon"}
            : new String[] {"helidon-4.3"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HelidonInstrumentation());
  }
}
