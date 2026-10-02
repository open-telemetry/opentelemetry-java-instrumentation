/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule extends InstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "async-http-client-2.0" : "async-http-client",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"async-http-client"}
            : new String[] {"async-http-client-2.0"});
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
