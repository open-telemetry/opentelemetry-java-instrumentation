/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.V3PreviewFallbackEnabledInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
@SuppressWarnings("deprecation") // using v3 preview fallback helper until 3.0
public class OpenTelemetryApiIncubatorInstrumentationModule
    extends V3PreviewFallbackEnabledInstrumentationModule
    implements ExperimentalInstrumentationModule {
  public OpenTelemetryApiIncubatorInstrumentationModule() {
    super("opentelemetry-api", "opentelemetry-api-1.32", "opentelemetry-api-incubator-1.32");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // this instrumentation module targets io.opentelemetry:opentelemetry-extension-incubator
    // added in 1.31
    return hasClassesNamed(
        "application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryIncubatorInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "opentelemetry-api-bridge";
  }
}
