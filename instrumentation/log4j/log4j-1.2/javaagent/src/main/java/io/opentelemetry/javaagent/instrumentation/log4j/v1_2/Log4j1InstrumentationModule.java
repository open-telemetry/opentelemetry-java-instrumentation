/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v1_2;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Log4j1InstrumentationModule extends InstrumentationModule {
  public Log4j1InstrumentationModule() {
    super("log4j", "log4j-1.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CategoryInstrumentation(), new LoggingEventInstrumentation());
  }
}
