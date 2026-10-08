/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OracleUcpInstrumentationModule extends InstrumentationModule {

  public OracleUcpInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "oracle-ucp-11.2" : "oracle-ucp",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"oracle-ucp"}
            : new String[] {"oracle-ucp-11.2"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PoolDataSourceInstrumentation(), new UniversalConnectionPoolInstrumentation());
  }
}
