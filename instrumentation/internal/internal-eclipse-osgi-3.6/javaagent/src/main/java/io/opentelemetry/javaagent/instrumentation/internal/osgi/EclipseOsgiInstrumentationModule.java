/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.osgi;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class EclipseOsgiInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public EclipseOsgiInstrumentationModule() {
    super("internal_eclipse_osgi", "internal_eclipse_osgi_3.6");
  }

  @Override
  public boolean defaultEnabled() {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new EclipseOsgiInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
