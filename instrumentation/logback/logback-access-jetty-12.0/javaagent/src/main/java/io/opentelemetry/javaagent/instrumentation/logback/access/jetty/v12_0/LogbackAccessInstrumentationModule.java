/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.List;

@AutoService(InstrumentationModule.class)
public class LogbackAccessInstrumentationModule extends InstrumentationModule {
  public LogbackAccessInstrumentationModule() {
    super("logback-access", "logback-access-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LogbackAccessInstrumentation());
  }
}
