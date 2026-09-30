/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WebfluxClientInstrumentationModule extends InstrumentationModule {

  public WebfluxClientInstrumentationModule() {
    super(
        "spring-webflux",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {
              "spring-webflux-5.0", "spring-webflux-client", "spring-webflux-5.0-client"
            }
            : new String[] {"spring-webflux-5.0", "spring-webflux-client"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new WebClientBuilderInstrumentation(),
        new ReactorClientHttpResponseInstrumentation(),
        new DefaultClientResponseInstrumentation());
  }
}
