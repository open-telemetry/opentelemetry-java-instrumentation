/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.flow;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesFlowInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesFlowInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "kotlinx-coroutines" : "kotlinx-coroutines-flow",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"kotlinx-coroutines-1.0", "kotlinx-coroutines-1.0-flow"}
            : new String[] {"kotlinx-coroutines-flow-1.3", "kotlinx-coroutines"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractFlowInstrumentation());
  }
}
