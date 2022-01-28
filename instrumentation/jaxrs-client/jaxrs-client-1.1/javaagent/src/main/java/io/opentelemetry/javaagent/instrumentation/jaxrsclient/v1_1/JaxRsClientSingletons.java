/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public class JaxRsClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxrs-client-1.1";

  private static final Instrumenter<ClientRequest, ClientResponse> INSTRUMENTER;

  static {
    JaxRsClientHttpAttributesGetter httpAttributesExtractor = new JaxRsClientHttpAttributesGetter();
    JaxRsClientNetAttributesGetter netAttributesGetter = new JaxRsClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(ClientRequestHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ClientRequest, ClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxRsClientSingletons() {}
}
