/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MetroInstrumentationModule extends InstrumentationModule {
  public MetroInstrumentationModule() {
    super("metro", "metro-2.2", "jaxws");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ServerTubeAssemblerContextInstrumentation(), new SoapFaultBuilderInstrumentation());
  }
}
