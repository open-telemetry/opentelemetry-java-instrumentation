/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxClientInstrumentationModule extends InstrumentationModule {

  public VertxClientInstrumentationModule() {
    super("vertx-client", "vertx");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ConnectionManagerInstrumentation(),
        new HttpClientConnectionInstrumentation(),
        new HttpRequestInstrumentation());
  }
}
