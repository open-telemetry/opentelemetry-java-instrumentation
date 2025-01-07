/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GatewayInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public GatewayInstrumentationModule() {
    super("spring-cloud-gateway", "spring-cloud-gateway-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HandlerAdapterInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    // relies on netty
    return "netty";
  }

  @Override
  public int order() {
    // Later than Spring Webflux.
    return 1;
  }
}
