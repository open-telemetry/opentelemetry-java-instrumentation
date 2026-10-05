/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gateway.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GatewayInstrumentationModule extends InstrumentationModule {

  public GatewayInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-cloud-gateway-2.0" : "spring-cloud-gateway",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-cloud-gateway"}
            : new String[] {"spring-cloud-gateway-2.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HandlerAdapterInstrumentation());
  }

  @Override
  public int order() {
    // Later than Spring Webflux.
    return 1;
  }
}
