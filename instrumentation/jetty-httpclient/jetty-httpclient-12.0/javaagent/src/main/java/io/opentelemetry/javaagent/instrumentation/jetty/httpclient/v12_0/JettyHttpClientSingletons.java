/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

public final class JettyHttpClientSingletons {

  static final String JETTY_CLIENT_CONTEXT_KEY = "otel-jetty-client-context";

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      JavaagentHttpClientInstrumenters.create(
          JettyHttpClientInstrumenterBuilderFactory.create(GlobalOpenTelemetry.get()));

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private JettyHttpClientSingletons() {}
}
