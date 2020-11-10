/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RatpackInstrumentationModule extends InstrumentationModule {
  public RatpackInstrumentationModule() {
    super("ratpack");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ActionWrapper",
      packageName + ".BlockWrapper",
      packageName + ".RatpackTracer",
      packageName + ".TracingHandler",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContinuationInstrumentation(),
        new DefaultExecutionInstrumentation(),
        new ServerErrorHandlerInstrumentation(),
        new ServerRegistryInstrumentation());
  }
}
