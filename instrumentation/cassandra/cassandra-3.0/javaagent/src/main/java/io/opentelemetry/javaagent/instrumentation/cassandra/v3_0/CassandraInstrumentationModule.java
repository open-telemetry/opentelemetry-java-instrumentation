/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CassandraInstrumentationModule extends InstrumentationModule {
  public CassandraInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "cassandra-3.0" : "cassandra",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"cassandra"}
            : new String[] {"cassandra-3.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CassandraBuilderInstrumentation(),
        new CassandraClusterInstrumentation(),
        new CassandraManagerInstrumentation());
  }
}
