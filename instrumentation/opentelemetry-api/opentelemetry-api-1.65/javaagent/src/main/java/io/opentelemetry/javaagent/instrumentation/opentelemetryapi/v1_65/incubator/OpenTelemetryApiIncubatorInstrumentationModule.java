/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiIncubatorInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiIncubatorInstrumentationModule() {
    super("opentelemetry-api-1.65", "opentelemetry-api-incubator", "opentelemetry-api");
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // this instrumentation module targets io.opentelemetry:opentelemetry-api-incubator
    return hasClassesNamed(
        // added in 1.65
        "application.io.opentelemetry.api.incubator.metrics.BoundLongCounter");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryIncubatorInstrumentation());
  }
}
