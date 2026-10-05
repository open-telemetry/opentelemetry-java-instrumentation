/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WithSpanInstrumentationModule extends InstrumentationModule {

  public WithSpanInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "opentelemetry-extension-annotations-1.0"
            : "opentelemetry-extension-annotations",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"opentelemetry-extension-annotations"}
            : new String[] {"opentelemetry-extension-annotations-1.0"});
  }

  @Override
  public int order() {
    // Run after other instrumentations.
    return 1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WithSpanInstrumentation());
  }
}
