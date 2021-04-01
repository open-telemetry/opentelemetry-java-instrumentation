/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ArmeriaInstrumentationModule extends InstrumentationModule {
  public ArmeriaInstrumentationModule() {
    super("armeria", "armeria-1.3");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Unrelated class which was added in Armeria 1.3.0, the minimum version we support.
    return hasClassesNamed("com.linecorp.armeria.server.metric.PrometheusExpositionServiceBuilder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ArmeriaWebClientBuilderInstrumentation(), new ArmeriaServerBuilderInstrumentation());
  }
}
