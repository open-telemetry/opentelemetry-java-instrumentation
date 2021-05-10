/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHandlerInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Jetty11InstrumentationModule extends InstrumentationModule {

  public Jetty11InstrumentationModule() {
    super("jetty", "jetty-11.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(
        new JettyHandlerInstrumentation(
            "jakarta.servlet",
            Jetty11InstrumentationModule.class.getPackage().getName() + ".Jetty11HandlerAdvice"));
  }
}
