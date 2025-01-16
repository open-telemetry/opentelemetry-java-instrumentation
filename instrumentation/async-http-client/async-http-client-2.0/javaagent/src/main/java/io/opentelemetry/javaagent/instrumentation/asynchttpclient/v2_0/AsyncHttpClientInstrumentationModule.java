/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule extends InstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AsyncHttpClientInstrumentation(),
        new AsyncCompletionHandlerInstrumentation(),
        new NettyRequestSenderInstrumentation(),
        new NettyResponseFutureInstrumentation());
  }
}
