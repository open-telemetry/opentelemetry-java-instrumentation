/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GrizzlyInstrumentationModule extends InstrumentationModule {
  public GrizzlyInstrumentationModule() {
    super("grizzly");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".GrizzlyHttpServerTracer", packageName + ".ExtractAdapter"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultFilterChainInstrumentation(),
        new FilterInstrumentation(),
        new HttpCodecFilterInstrumentation(),
        new HttpServerFilterInstrumentation());
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }
}
