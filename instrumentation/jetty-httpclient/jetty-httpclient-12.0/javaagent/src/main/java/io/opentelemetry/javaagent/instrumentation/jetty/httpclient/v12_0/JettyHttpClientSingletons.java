/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpRequest;

public class JettyHttpClientSingletons {

  static final String JETTY_CLIENT_CONTEXT_KEY = "otel-jetty-client-context";
  public static final VirtualField<HttpRequest, Context> REQUEST_CONTEXT =
      VirtualField.find(HttpRequest.class, Context.class);

  private static final Instrumenter<Request, Response> instrumenter =
      JavaagentHttpClientInstrumenters.create(
          JettyHttpClientInstrumenterBuilderFactory.create(GlobalOpenTelemetry.get()));

  public static Instrumenter<Request, Response> instrumenter() {
    return instrumenter;
  }

  private JettyHttpClientSingletons() {}
}
