/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Servlet3InstrumentationModule extends InstrumentationModule {
  private static final String BASE_PACKAGE = "javax.servlet";

  public Servlet3InstrumentationModule() {
    super("servlet", "servlet-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AsyncContextInstrumentation(BASE_PACKAGE, adviceClassName(".AsyncDispatchAdvice")),
        new ServletAndFilterInstrumentation(BASE_PACKAGE, adviceClassName(".Servlet3Advice")));
  }

  private static String adviceClassName(String suffix) {
    return Servlet3InstrumentationModule.class.getPackage().getName() + suffix;
  }
}
