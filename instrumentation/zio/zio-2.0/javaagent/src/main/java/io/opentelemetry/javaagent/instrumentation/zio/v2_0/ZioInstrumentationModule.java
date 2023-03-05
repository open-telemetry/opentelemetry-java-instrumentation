/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ZioInstrumentationModule extends InstrumentationModule {

  public ZioInstrumentationModule() {
    super("zio");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ZioRuntimeInstrumentation());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return asList(
        "io.opentelemetry.javaagent.instrumentation.zio.v2_0.FiberContext",
        "io.opentelemetry.javaagent.instrumentation.zio.v2_0.TracingSupervisor");
  }
}
