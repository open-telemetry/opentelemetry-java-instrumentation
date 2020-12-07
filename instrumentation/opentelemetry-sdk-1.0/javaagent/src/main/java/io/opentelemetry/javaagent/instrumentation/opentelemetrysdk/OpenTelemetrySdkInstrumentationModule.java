/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetrysdk;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenTelemetrySdkInstrumentationModule extends InstrumentationModule {
  public OpenTelemetrySdkInstrumentationModule() {
    super("opentelemetry-sdk");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new OpenTelemetrySdkInstrumentation());
  }
}
