/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

public class ResteasyClientSingletons {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.jaxrs-client-2.0-resteasy-3.0";

  private static final Instrumenter<ClientInvocation, Response> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<ClientInvocation, Response> httpAttributesExtractor =
        new ResteasyClientHttpAttributesExtractor();
    SpanNameExtractor<? super ClientInvocation> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super ClientInvocation, ? super Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ResteasyClientNetAttributesExtractor netAttributesExtractor =
        new ResteasyClientNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<ClientInvocation, Response>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .setErrorCauseExtractor(
                (throwable) -> {
                  if (throwable instanceof ProcessingException) {
                    throwable = throwable.getCause();
                  }
                  return ErrorCauseExtractor.jdk().extractCause(throwable);
                })
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(new ResteasyInjectAdapter());
  }

  public static Instrumenter<ClientInvocation, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private ResteasyClientSingletons() {}
}
