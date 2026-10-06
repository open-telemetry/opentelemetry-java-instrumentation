/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v2_3;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Struts2InstrumentationModule extends InstrumentationModule {

  public Struts2InstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "struts-2.3" : "struts",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"struts"}
            : new String[] {"struts-2.3"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActionInvocationInstrumentation());
  }
}
