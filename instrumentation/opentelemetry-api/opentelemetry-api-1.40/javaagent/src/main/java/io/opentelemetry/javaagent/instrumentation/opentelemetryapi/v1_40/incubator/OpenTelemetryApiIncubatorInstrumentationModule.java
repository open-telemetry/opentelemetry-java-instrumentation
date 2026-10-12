/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiIncubatorInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiIncubatorInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "opentelemetry-api-1.40" : "opentelemetry-api",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-api-incubator", "opentelemetry-api"}
            : new String[] {"opentelemetry-api-1.40", "opentelemetry-api-incubator-1.40"});
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 1.40
        "application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram",
        // removed in 1.47
        "application.io.opentelemetry.api.incubator.events.EventLogger");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryIncubatorInstrumentation());
  }
}
