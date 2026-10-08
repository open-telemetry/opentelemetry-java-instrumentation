/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HikariCpInstrumentationModule extends InstrumentationModule {

  public HikariCpInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "hikaricp-3.0" : "hikaricp",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"hikaricp"}
            : new String[] {"hikaricp-3.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HikariConfigInstrumentation(), new HikariPoolInstrumentation());
  }
}
