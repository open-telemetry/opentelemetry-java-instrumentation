/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HibernateReactiveInstrumentationModule extends InstrumentationModule {

  public HibernateReactiveInstrumentationModule() {
    super("hibernate-reactive");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new StageSessionFactoryInstrumentation(), new MutinySessionFactoryInstrumentation());
  }
}
