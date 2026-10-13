/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.eclipse.osgi.v3_6;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class EclipseOsgiInstrumentationModule extends InstrumentationModule {
  public EclipseOsgiInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "internal-eclipse-osgi-3.6"
            : "internal-eclipse-osgi",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"internal-eclipse-osgi"}
            : new String[] {"internal-eclipse-osgi-3.6"});
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
}
