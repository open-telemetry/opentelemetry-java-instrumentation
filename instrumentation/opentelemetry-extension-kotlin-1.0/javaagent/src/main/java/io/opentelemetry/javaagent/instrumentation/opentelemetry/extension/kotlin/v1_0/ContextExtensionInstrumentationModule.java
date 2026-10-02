/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetry.extension.kotlin.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ContextExtensionInstrumentationModule extends InstrumentationModule {

  public ContextExtensionInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "opentelemetry-extension-kotlin-1.0"
            : "opentelemetry-extension-kotlin",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-extension-kotlin"}
            : new String[] {"opentelemetry-extension-kotlin-1.0"});
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextExtensionInstrumentation());
  }
}
