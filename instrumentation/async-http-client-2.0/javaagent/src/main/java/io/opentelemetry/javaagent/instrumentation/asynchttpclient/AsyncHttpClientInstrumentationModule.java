/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(io.opentelemetry.javaagent.tooling.InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule
    extends io.opentelemetry.javaagent.tooling.InstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RequestInstrumentation(),
        new ResponseInstrumentation(),
        new RequestSenderInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> stores = new java.util.HashMap<>();
    stores.put("org.asynchttpclient.AsyncHandler", Pair.class.getName());
    stores.put("org.asynchttpclient.Request", Context.class.getName());
    return stores;
  }
}
