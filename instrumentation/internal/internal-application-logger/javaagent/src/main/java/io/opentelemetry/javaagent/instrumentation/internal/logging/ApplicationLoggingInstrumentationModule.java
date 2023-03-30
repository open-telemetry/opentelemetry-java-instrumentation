/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApplicationLoggingInstrumentationModule extends InstrumentationModule {

  public ApplicationLoggingInstrumentationModule() {
    super("internal-application-logging");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // only enable this instrumentation if the application logger is enabled
    return config.getString("otel.javaagent.logging", "simple").equalsIgnoreCase("application");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LoggerFactoryInstrumentation());
  }
}
