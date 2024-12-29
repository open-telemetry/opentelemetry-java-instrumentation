/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenTelemetryApiInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public OpenTelemetryApiInstrumentationModule() {
    super("opentelemetry-api", "opentelemetry-api-1.37", "opentelemetry-api-incubator-1.37");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // skip instrumentation when opentelemetry-api-incubator is not present
    return hasClassesNamed("application.io.opentelemetry.api.incubator.metrics.DoubleGauge");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "opentelemetry-api-bridge";
  }
}
