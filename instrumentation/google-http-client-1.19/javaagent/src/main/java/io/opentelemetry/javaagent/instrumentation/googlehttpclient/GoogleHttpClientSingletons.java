/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;

public class GoogleHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.google-http-client-1.19";

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenterBuilder.create(
                INSTRUMENTATION_NAME, new GoogleHttpClientHttpAttributesGetter())
            .buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private GoogleHttpClientSingletons() {}
}
