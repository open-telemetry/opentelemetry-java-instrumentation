/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHandlerInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Jetty8InstrumentationModule extends InstrumentationModule {

  public Jetty8InstrumentationModule() {
    super("jetty", "jetty-8.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new JettyHandlerInstrumentation(
            "javax.servlet",
            Jetty8InstrumentationModule.class.getPackage().getName() + ".Jetty8HandlerAdvice"),
        new JettyQueuedThreadPoolInstrumentation());
  }
}
