/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-1.9");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RequestInstrumentation(), new ResponseInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
