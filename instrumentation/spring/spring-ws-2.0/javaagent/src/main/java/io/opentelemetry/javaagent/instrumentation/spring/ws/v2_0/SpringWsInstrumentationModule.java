/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringWsInstrumentationModule extends InstrumentationModule {
  public SpringWsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-ws-2.0" : "spring-ws",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-ws"}
            : new String[] {"spring-ws-2.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AnnotatedMethodInstrumentation());
  }

  @Override
  public boolean defaultEnabled() {
    // this instrumentation only produces controller telemetry
    return super.defaultEnabled() && ExperimentalConfig.get().controllerTelemetryEnabled();
  }
}
