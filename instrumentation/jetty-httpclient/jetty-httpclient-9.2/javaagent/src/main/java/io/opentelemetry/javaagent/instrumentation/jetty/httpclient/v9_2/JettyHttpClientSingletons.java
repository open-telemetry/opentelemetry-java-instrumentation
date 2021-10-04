/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyHttpClientNetAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesOnStartExtractor;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  private JettyHttpClientSingletons() {}

  static {
    JettyClientInstrumenterBuilder builder =
        new JettyClientInstrumenterBuilder(GlobalOpenTelemetry.get());

    PeerServiceAttributesOnStartExtractor<Request, Response> peerServiceAttributesExtractor =
        PeerServiceAttributesOnStartExtractor.create(new JettyHttpClientNetAttributesExtractor());
    INSTRUMENTER = builder.addAttributeExtractor(peerServiceAttributesExtractor).build();
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }
}
