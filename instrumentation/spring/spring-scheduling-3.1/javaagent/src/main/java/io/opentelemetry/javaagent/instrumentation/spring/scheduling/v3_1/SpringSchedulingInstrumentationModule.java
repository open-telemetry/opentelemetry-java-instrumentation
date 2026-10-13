/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringSchedulingInstrumentationModule extends InstrumentationModule {

  public SpringSchedulingInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-scheduling-3.1" : "spring-scheduling",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-scheduling"}
            : new String[] {"spring-scheduling-3.1"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new TaskSchedulerInstrumentation(), new DelegatingErrorHandlingRunnableInstrumentation());
  }
}
