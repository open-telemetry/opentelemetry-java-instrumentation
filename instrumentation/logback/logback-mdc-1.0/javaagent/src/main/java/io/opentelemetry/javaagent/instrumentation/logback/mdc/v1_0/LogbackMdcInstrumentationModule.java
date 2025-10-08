/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LogbackMdcInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public LogbackMdcInstrumentationModule() {
    super("logback-mdc", "logback-mdc-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new LoggerInstrumentation(), new LoggingEventInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public int order() {
    // run before logback appender instrumentation so that the appender instrumentation can observe
    // the attributes added to the mdc by this instrumentation
    return -1;
  }
}
