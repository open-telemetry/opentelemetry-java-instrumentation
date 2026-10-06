/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_57;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.V3PreviewFallbackEnabledInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
@SuppressWarnings("deprecation") // using v3 preview fallback helper until 3.0
public class OpenTelemetryApiInstrumentationModule
    extends V3PreviewFallbackEnabledInstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "opentelemetry-api-1.57" : "opentelemetry-api",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-api"}
            : new String[] {"opentelemetry-api-1.57"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }
}
