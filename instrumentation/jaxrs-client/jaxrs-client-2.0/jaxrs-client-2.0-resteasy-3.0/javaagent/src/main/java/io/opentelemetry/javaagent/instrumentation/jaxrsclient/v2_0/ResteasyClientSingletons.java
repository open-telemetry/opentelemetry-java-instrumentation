/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

public class ResteasyClientSingletons {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.jaxrs-client-2.0-resteasy-3.0";

  private static final Instrumenter<ClientInvocation, Response> INSTRUMENTER;

  static {
    ResteasyClientHttpAttributesGetter httpAttributesExtractor =
        new ResteasyClientHttpAttributesGetter();
    ResteasyClientNetAttributesGetter netAttributesGetter = new ResteasyClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ClientInvocation, Response>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .setErrorCauseExtractor(
                (throwable) -> {
                  if (throwable instanceof ProcessingException) {
                    throwable = throwable.getCause();
                  }
                  return ErrorCauseExtractor.jdk().extractCause(throwable);
                })
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(ClientInvocationHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ClientInvocation, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private ResteasyClientSingletons() {}
}
