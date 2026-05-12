/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.mutiny;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HibernateReactiveMutinyInstrumentationModule extends InstrumentationModule {

  public HibernateReactiveMutinyInstrumentationModule() {
    super("hibernate-reactive", "hibernate-reactive-1.0", "hibernate-reactive-mutiny");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new MutinySessionFactoryInstrumentation());
  }
}
