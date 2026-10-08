/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "kotlinx-coroutines-1.0" : "kotlinx-coroutines",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"kotlinx-coroutines"}
            : new String[] {"kotlinx-coroutines-1.0", "kotlinx-coroutines-1.0-core"});
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KotlinCoroutinesInstrumentation(), new KotlinCoroutineDispatcherInstrumentation());
  }
}
