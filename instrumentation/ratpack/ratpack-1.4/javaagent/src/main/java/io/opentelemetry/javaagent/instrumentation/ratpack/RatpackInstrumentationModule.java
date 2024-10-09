/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RatpackInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public RatpackInstrumentationModule() {
    super("ratpack", "ratpack-1.4");
  }

  @Override
  public String getModuleGroup() {
    // relies on netty
    return "netty";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContinuationInstrumentation(),
        new ContinuationStreamInstrumentation(),
        new DefaultExecutionInstrumentation(),
        new DefaultExecStarterInstrumentation(),
        new ExecutionBoundPublisherInstrumentation(),
        new ServerErrorHandlerInstrumentation(),
        new ServerRegistryInstrumentation());
  }
}
