/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.V3PreviewFallbackEnabledInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
@SuppressWarnings("deprecation") // using v3 preview fallback helper until 3.0
public class OpenTelemetryApiInstrumentationModule
    extends V3PreviewFallbackEnabledInstrumentationModule
    implements ExperimentalInstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super("opentelemetry-api", "opentelemetry-api-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContextInstrumentation(),
        new ContextStorageWrappersInstrumentation(),
        new OpenTelemetryInstrumentation(),
        new SpanInstrumentation(),
        new InstrumentationUtilInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "opentelemetry-api-bridge";
  }

  @Override
  public List<String> agentPackagesToHide() {
    // These are helper classes injected by api-version specific instrumentation modules
    // We don't want to fall back to accidentally trying to load those from the agent CL
    // when they haven't been injected
    return singletonList("io.opentelemetry.javaagent.instrumentation.opentelemetryapi");
  }
}
