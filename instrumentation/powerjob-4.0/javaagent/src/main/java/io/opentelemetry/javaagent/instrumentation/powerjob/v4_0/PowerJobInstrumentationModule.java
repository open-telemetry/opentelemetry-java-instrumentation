/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PowerJobInstrumentationModule extends InstrumentationModule {
  public PowerJobInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "powerjob-4.0" : "powerjob",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"powerjob"}
            : new String[] {"powerjob-4.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new BasicProcessorInstrumentation());
  }
}
