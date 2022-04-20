/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.instrumentationapi.HttpRouteStateInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super("opentelemetry-api");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContextInstrumentation(),
        new ContextStorageWrappersInstrumentation(),
        new OpenTelemetryInstrumentation(),
        new SpanInstrumentation(),
        // instrumentation-api specific instrumentation
        new HttpRouteStateInstrumentation());
  }
}
