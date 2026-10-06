/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GrailsInstrumentationModule extends InstrumentationModule {
  public GrailsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "grails-3.0" : "grails",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"grails"}
            : new String[] {"grails-3.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultGrailsControllerClassInstrumentation(),
        new UrlMappingsInfoHandlerAdapterInstrumentation());
  }
}
