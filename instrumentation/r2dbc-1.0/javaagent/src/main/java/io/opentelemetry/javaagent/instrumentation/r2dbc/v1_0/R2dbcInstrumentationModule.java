/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class R2dbcInstrumentationModule extends InstrumentationModule {

  public R2dbcInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "r2dbc-1.0" : "r2dbc",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"r2dbc"}
            : new String[] {"r2dbc-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new R2dbcInstrumentation());
  }
}
