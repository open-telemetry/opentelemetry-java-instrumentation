/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WebfluxClientInstrumentationModule extends InstrumentationModule {

  public WebfluxClientInstrumentationModule() {
    super("spring_webflux", "spring_webflux_5.0", "spring_webflux_client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WebClientBuilderInstrumentation());
  }
}
