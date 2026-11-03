/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiIncubatorInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiIncubatorInstrumentationModule() {
    super("opentelemetry-api-1.47", "opentelemetry-api-incubator", "opentelemetry-api");
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
            // added in 1.42
            "application.io.opentelemetry.api.common.Value",
            // added in 1.40
            "application.io.opentelemetry.api.incubator.logs.ExtendedLogger")
        .and(
            // removed in 1.47
            not(hasClassesNamed("application.io.opentelemetry.api.incubator.events.EventLogger")))
        .and(
            // added in 1.50
            not(
                hasClassesNamed(
                    "application.io.opentelemetry.api.incubator.common.ExtendedAttributes")))
        .and(
            // added in 1.63
            not(hasClassesNamed("application.io.opentelemetry.api.impl.InstrumentationUtil")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryIncubatorInstrumentation());
  }
}
