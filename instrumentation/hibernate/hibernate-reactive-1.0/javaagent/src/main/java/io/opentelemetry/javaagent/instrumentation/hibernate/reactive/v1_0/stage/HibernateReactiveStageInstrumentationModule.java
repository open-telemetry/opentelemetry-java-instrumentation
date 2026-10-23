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
    // The hibernate selector groups default-off telemetry, not default-on Reactive propagation.
    // Keep propagation enabled so asynchronous database spans retain their parent context.
    super("hibernate-reactive-1.0", "hibernate-reactive");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new StageSessionFactoryInstrumentation(), new StageSessionImplInstrumentation());
  }
}
