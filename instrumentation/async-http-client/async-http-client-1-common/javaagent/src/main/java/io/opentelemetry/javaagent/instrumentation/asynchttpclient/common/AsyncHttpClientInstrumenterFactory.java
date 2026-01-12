/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;

public final class AsyncHttpClientInstrumenterFactory {

  public static Instrumenter<Request, Response> create(
      String instrumentationName, AsyncHttpClientHelper helper) {
    AsyncHttpClientHttpAttributesGetter httpAttributesGetter =
        new AsyncHttpClientHttpAttributesGetter(helper);
    HttpHeaderSetter headerSetter = new HttpHeaderSetter(helper);

    return JavaagentHttpClientInstrumenters.create(
        instrumentationName, httpAttributesGetter, headerSetter);
  }

  private AsyncHttpClientInstrumenterFactory() {}
}
