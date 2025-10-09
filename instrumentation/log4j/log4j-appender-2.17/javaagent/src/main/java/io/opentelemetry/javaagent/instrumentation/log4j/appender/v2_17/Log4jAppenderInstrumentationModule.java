/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_17;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Log4jAppenderInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public Log4jAppenderInstrumentationModule() {
    super("log4j-appender", "log4j-appender-2.17");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new Log4jAppenderInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
