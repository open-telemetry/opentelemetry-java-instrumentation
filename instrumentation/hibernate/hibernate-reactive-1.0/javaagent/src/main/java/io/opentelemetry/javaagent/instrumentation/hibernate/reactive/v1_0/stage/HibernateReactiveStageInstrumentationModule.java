/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.stage;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HibernateReactiveStageInstrumentationModule extends InstrumentationModule {

  public HibernateReactiveStageInstrumentationModule() {
    super("hibernate_reactive", "hibernate_reactive_1.0", "hibernate_reactive_stage");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new StageSessionFactoryInstrumentation(), new StageSessionImplInstrumentation());
  }
}
