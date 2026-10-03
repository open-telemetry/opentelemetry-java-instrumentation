/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Log4jAppenderInstrumentationModule extends InstrumentationModule {

  public Log4jAppenderInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "log4j-appender-1.2" : "log4j-appender",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"log4j-appender", "log4j"}
            : new String[] {"log4j-appender-1.2"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new Log4jAppenderInstrumentation());
  }
}
