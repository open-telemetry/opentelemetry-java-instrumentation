/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiInstrumentationModule extends InstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super("opentelemetry-api-1.32", "opentelemetry-api");
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // skip instrumentation when opentelemetry-extension-incubator is present, instrumentation is
    // handled by OpenTelemetryApiIncubatorInstrumentationModule
    // added in 1.32
    return hasClassesNamed("application.io.opentelemetry.api.logs.LoggerBuilder")
        // added in 1.31
        .and(
            not(
                hasClassesNamed(
                    "application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }
}
