/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiInstrumentationModule extends InstrumentationModule {

  public OpenTelemetryApiInstrumentationModule() {
    super("opentelemetry-api-1.10", "opentelemetry-api");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }
}
