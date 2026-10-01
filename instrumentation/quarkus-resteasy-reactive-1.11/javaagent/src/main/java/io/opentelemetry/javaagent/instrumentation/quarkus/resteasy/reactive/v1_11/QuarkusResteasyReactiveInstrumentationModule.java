/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive.v1_11;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class QuarkusResteasyReactiveInstrumentationModule extends InstrumentationModule {

  public QuarkusResteasyReactiveInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "quarkus-resteasy-reactive" : "quarkus",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"quarkus-resteasy-reactive-1.11", "jaxrs", "quarkus"}
            : expandDeprecatedNames(
                "jaxrs",
                "quarkus-resteasy-reactive",
                "quarkus-resteasy-reactive-1.11|deprecated:quarkus-resteasy-reactive-3.0"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractResteasyReactiveContextInstrumentation(),
        new InvocationHandlerInstrumentation());
  }
}
