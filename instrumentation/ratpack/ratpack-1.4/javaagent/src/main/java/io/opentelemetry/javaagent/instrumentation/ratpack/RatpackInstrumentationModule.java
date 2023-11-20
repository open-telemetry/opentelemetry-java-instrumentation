/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RatpackInstrumentationModule extends InstrumentationModule {
  public RatpackInstrumentationModule() {
    super("ratpack", "ratpack-1.4");
  }

  @Override
  public boolean isIndyModule() {
    // java.lang.ClassCastException: class
    // io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.AutoValue_ServerContext
    // cannot be cast to class
    // io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.ServerContext
    // (io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.AutoValue_ServerContext is in unnamed module of loader 'app'; io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.ServerContext is in unnamed module of loader io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationModuleClassLoader @7f088b5c)
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContinuationInstrumentation(),
        new DefaultExecutionInstrumentation(),
        new DefaultExecStarterInstrumentation(),
        new ServerErrorHandlerInstrumentation(),
        new ServerRegistryInstrumentation());
  }
}
