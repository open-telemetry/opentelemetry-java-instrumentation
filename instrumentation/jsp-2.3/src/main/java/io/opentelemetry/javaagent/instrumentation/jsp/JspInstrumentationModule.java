/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JspInstrumentationModule extends InstrumentationModule {
  public JspInstrumentationModule() {
    super("jsp");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JspTracer",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpJspPageInstrumentation(), new JspCompilationContextInstrumentation());
  }
}
