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
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApplicationLoggingInstrumentationModule extends InstrumentationModule {

  public ApplicationLoggingInstrumentationModule() {
    super("internal-application-logger");
  }

  @Override
  public boolean defaultEnabled() {
    // only enable this instrumentation if the application logger is enabled
    return super.defaultEnabled(config)
        && "application".equals(EarlyInitAgentConfig.get().getLogging());
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LoggerFactoryInstrumentation(),
        new SpringApplicationInstrumentation(),
        new LoggingApplicationListenerInstrumentation());
  }
}
