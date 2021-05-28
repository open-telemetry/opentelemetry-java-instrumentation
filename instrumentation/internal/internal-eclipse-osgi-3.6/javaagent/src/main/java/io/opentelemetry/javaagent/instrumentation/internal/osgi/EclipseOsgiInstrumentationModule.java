/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.osgi;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class EclipseOsgiInstrumentationModule extends InstrumentationModule {
  public EclipseOsgiInstrumentationModule() {
    super("internal-eclipse-osgi");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new EclipseOsgiInstrumentation());
  }
}
