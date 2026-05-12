/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;

public class PekkoHttpClientSingletons {

  private static final HttpHeaderSetter setter;
  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    setter = new HttpHeaderSetter(GlobalOpenTelemetry.getPropagators());

    instrumenter =
        JavaagentHttpClientInstrumenters.create(
            PekkoHttpUtil.instrumentationName(), new PekkoHttpClientAttributesGetter());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  public static HttpHeaderSetter setter() {
    return setter;
  }

  private PekkoHttpClientSingletons() {}
}
