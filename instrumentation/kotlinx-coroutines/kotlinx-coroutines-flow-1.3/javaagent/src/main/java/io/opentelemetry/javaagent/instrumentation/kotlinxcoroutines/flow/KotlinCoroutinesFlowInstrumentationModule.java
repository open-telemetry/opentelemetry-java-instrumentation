/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.flow;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesFlowInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesFlowInstrumentationModule() {
    super("kotlinx_coroutines_flow", "kotlinx_coroutines_flow_1.3", "kotlinx_coroutines");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractFlowInstrumentation());
  }
}
