/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi.v1_14;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class InstrumentationApiInstrumentationModule extends InstrumentationModule {

  public InstrumentationApiInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "opentelemetry-instrumentation-api-1.14"
            : "opentelemetry-instrumentation-api",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-instrumentation-api"}
            : new String[] {"opentelemetry-instrumentation-api-1.14"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpRouteStateInstrumentation(), new SpanKeyInstrumentation());
  }
}
