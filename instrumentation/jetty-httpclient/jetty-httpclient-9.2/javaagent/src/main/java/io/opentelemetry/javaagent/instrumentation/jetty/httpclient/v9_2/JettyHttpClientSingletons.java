/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyHttpClientNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      new JettyClientInstrumenterBuilder(GlobalOpenTelemetry.get())
          .addAttributeExtractor(
              PeerServiceAttributesExtractor.create(
                  new JettyHttpClientNetAttributesGetter(),
                  CommonConfig.get().getPeerServiceMapping()))
          .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
          .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
          .build();

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private JettyHttpClientSingletons() {}
}
