/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Log4j1InstrumentationModule extends InstrumentationModule {
  public Log4j1InstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "log4j-mdc-1.2" : "log4j-mdc",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"log4j-mdc", "log4j"}
            : new String[] {"log4j-mdc-1.2"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.2
    return hasClassesNamed("org.apache.log4j.MDC");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CategoryInstrumentation(), new LoggingEventInstrumentation());
  }
}
