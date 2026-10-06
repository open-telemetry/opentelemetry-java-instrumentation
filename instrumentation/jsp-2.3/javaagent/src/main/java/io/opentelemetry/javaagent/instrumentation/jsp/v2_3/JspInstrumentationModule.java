/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp.v2_3;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JspInstrumentationModule extends InstrumentationModule {
  public JspInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "jsp-2.3" : "jsp",
        AgentCommonConfig.get().isV3Preview() ? new String[] {"jsp"} : new String[] {"jsp-2.3"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpJspPageInstrumentation(), new JspCompilationContextInstrumentation());
  }
}
