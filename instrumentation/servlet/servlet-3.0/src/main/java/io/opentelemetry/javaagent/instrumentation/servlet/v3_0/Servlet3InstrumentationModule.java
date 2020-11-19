/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Servlet3InstrumentationModule extends InstrumentationModule {
  public Servlet3InstrumentationModule() {
    super("servlet", "servlet-3.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.servlet.HttpServletRequestGetter",
      "io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer",
      packageName + ".Servlet3Advice",
      packageName + ".Servlet3HttpServerTracer",
      packageName + ".TagSettingAsyncListener"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AsyncContextInstrumentation(), new ServletAndFilterInstrumentation());
  }
}
