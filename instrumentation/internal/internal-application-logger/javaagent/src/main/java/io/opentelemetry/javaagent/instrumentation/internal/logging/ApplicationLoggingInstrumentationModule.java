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
    String logging =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
            .getString("logging");
    return superDefaultEnabled() && "application".equals(logging);
  }

  // This method can be removed and super.defaultEnabled() can be used instead once the deprecated
  // InstrumentationModule.defaultEnabled(ConfigProperties) is removed, at which point
  // InstrumentationModule.defaultEnabled() will no longer need to throw an exception.
  private static boolean superDefaultEnabled() {
    return DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
        .getBoolean("default_enabled", true);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LoggerFactoryInstrumentation(),
        new SpringApplicationInstrumentation(),
        new LoggingApplicationListenerInstrumentation());
  }
}
