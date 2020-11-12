/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v1_2;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class Log4j1InstrumentationModule extends InstrumentationModule {
  public Log4j1InstrumentationModule() {
    super("log4j1", "log4j");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CategoryInstrumentation(), new LoggingEventInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.apache.log4j.spi.LoggingEvent", Span.class.getName());
  }
}
