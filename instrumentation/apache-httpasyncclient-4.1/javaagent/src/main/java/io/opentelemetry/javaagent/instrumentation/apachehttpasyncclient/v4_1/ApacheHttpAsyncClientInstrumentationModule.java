/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheHttpAsyncClientInstrumentationModule extends InstrumentationModule {
  public ApacheHttpAsyncClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "apache-httpasyncclient-4.1"
            : "apache-httpasyncclient",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"apache-httpasyncclient"}
            : new String[] {"apache-httpasyncclient-4.1"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ApacheHttpAsyncClientInstrumentation(),
        new ApacheHttpPipeliningClientInstrumentation());
  }
}
