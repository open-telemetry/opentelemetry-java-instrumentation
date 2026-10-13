/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "opentelemetry-api-1.4" : "opentelemetry-api",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-api"}
            : new String[] {"opentelemetry-api-1.4"});
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }
}
