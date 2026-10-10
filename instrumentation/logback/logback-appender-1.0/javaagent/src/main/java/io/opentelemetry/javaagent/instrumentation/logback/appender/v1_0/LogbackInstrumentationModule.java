/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LogbackInstrumentationModule extends InstrumentationModule {

  public LogbackInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "logback-appender-1.0" : "logback-appender",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"logback-appender", "logback"}
            : new String[] {"logback-appender-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LogbackInstrumentation());
  }
}
