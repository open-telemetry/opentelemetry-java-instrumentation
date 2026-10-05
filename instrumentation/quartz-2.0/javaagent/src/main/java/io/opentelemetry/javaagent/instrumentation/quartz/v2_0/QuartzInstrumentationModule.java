/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class QuartzInstrumentationModule extends InstrumentationModule {

  public QuartzInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "quartz-2.0" : "quartz",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"quartz"}
            : new String[] {"quartz-2.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new QuartzInstrumentation());
  }
}
