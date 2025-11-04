/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_8;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.AsyncHttpClientHttpAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.HttpHeaderSetter;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-1.8";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    AsyncHttpClientHttpAttributesGetter httpAttributesGetter =
        new AsyncHttpClientHttpAttributesGetter(AsyncHttpClient18Helper.INSTANCE);
    HttpHeaderSetter headerSetter = new HttpHeaderSetter(AsyncHttpClient18Helper.INSTANCE);

    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME, httpAttributesGetter, headerSetter);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
