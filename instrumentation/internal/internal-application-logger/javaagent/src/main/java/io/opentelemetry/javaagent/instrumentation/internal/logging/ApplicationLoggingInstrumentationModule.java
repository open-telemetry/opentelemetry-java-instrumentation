/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApplicationLoggingInstrumentationModule extends InstrumentationModule {

  public ApplicationLoggingInstrumentationModule() {
    super("internal-application-logger");
  }

  @Override
  public boolean defaultEnabled() {
    // only enable this instrumentation if the application logger is enabled
    return super.defaultEnabled()
        && DeclarativeConfigUtil.getString(GlobalOpenTelemetry.get(), "java", "agent", "logging")
            .map(value -> "application".equals(value))
            .orElse(false);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LoggerFactoryInstrumentation(),
        new SpringApplicationInstrumentation(),
        new LoggingApplicationListenerInstrumentation());
  }
}
