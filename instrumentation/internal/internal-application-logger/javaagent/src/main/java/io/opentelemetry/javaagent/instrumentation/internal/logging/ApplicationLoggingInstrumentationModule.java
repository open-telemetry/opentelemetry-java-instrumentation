/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApplicationLoggingInstrumentationModule extends InstrumentationModule {

  public ApplicationLoggingInstrumentationModule() {
    super("internal-application-logger");
  }

  // This module needs to use ConfigProperties (not GlobalOpenTelemetry) because its enabled check
  // runs very early during agent startup, before GlobalOpenTelemetry is set up. At that point,
  // GlobalOpenTelemetry.get() would return a noop instance that doesn't have the config.
  @SuppressWarnings("deprecation")
  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // only enable this instrumentation if the application logger is enabled
    return super.defaultEnabled(config)
        && "application".equals(config.getString("otel.javaagent.logging"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LoggerFactoryInstrumentation(),
        new SpringApplicationInstrumentation(),
        new LoggingApplicationListenerInstrumentation());
  }
}
