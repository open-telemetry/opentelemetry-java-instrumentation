/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class DruidInstrumentationModule extends InstrumentationModule {

  public DruidInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "alibaba-druid-1.0" : "alibaba-druid",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"alibaba-druid"}
            : new String[] {"alibaba-druid-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DruidDataSourceInstrumentation());
  }
}
