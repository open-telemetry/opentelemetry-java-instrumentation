/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MyBatisInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public MyBatisInstrumentationModule() {
    super("mybatis", "mybatis-3.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new MapperMethodInstrumentation(), new SqlCommandInstrumentation());
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
