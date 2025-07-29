/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionkotlin;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ContextExtensionInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ContextExtensionInstrumentationModule() {
    super("opentelemetry-extension-kotlin", "opentelemetry-extension-kotlin-1.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextExtensionInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "opentelemetry-api-bridge";
  }
}
