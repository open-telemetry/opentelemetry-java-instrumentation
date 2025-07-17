/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringSchedulingInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringSchedulingInstrumentationModule() {
    super("spring-scheduling", "spring-scheduling-3.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new TaskSchedulerInstrumentation(), new DelegatingErrorHandlingRunnableInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
