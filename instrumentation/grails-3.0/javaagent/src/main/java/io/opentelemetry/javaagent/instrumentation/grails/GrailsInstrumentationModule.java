/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GrailsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public GrailsInstrumentationModule() {
    super("grails", "grails-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultGrailsControllerClassInstrumentation(),
        new UrlMappingsInfoHandlerAdapterInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
