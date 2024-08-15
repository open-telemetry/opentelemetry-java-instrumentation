/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.0";

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME,
            new ApacheHttpClientHttpAttributesGetter(),
            HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientSingletons() {}
}
