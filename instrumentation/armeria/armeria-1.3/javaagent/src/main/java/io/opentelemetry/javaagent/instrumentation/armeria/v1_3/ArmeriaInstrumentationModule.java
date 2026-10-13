/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ArmeriaInstrumentationModule extends InstrumentationModule {
  public ArmeriaInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "armeria-1.3" : "armeria",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"armeria"}
            : new String[] {"armeria-1.3"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.3.0
    return hasClassesNamed("com.linecorp.armeria.server.metric.PrometheusExpositionServiceBuilder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ArmeriaWebClientBuilderInstrumentation(),
        new ArmeriaServerBuilderInstrumentation(),
        new AbstractStreamMessageSubscriptionInstrumentation());
  }
}
