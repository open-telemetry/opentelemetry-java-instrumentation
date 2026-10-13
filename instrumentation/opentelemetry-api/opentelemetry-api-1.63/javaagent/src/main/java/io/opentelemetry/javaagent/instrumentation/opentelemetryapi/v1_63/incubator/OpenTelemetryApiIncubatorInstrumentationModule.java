/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.incubator;

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
    super("opentelemetry-api-1.63", "opentelemetry-api-incubator", "opentelemetry-api");
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // this instrumentation module targets io.opentelemetry:opentelemetry-api-incubator
    return hasClassesNamed(
            // added in 1.63
            "application.io.opentelemetry.api.impl.InstrumentationUtil",
            // added in 1.42
            "application.io.opentelemetry.api.incubator.logs.ExtendedLogger")
        .and(
            // added in 1.65
            not(
                hasClassesNamed(
                    "application.io.opentelemetry.api.incubator.metrics.BoundLongCounter")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryIncubatorInstrumentation());
  }
}
