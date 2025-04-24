/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import static java.util.Collections.singletonList;

import java.util.List;

import com.google.auto.service.AutoService;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

@AutoService(InstrumentationModule.class)
public class HelidonInstrumentationModule extends InstrumentationModule {
  public HelidonInstrumentationModule() {
    super("helidon");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HelidonInstrumentation());
  }
}
