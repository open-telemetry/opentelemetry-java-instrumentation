/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rxjava.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RxJava2InstrumentationModule extends InstrumentationModule {

  public RxJava2InstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "rxjava-2.0" : "rxjava",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"rxjava"}
            : new String[] {"rxjava-2.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RxJavaPluginsInstrumentation());
  }
}
