/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38;

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
    super("opentelemetry-api-1.38", "opentelemetry-api");
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // this instrumentation module targets io.opentelemetry:opentelemetry-api
    // added in 1.38.0
    return hasClassesNamed("application.io.opentelemetry.api.metrics.LongGauge")
        // artifact presence gate
        // added in io.opentelemetry:opentelemetry-api-incubator 1.37.0
        // (renamed from io.opentelemetry.extension.incubator)
        .and(
            not(
                hasClassesNamed(
                    "application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }
}
