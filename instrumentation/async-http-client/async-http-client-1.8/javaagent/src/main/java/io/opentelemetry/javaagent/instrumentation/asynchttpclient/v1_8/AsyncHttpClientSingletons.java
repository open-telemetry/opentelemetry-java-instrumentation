/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_8;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.v1_8.AsyncHttpClientInstrumenterFactory;

class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-1.8";

  private static final Instrumenter<Request, Response> instrumenter =
      AsyncHttpClientInstrumenterFactory.create(
          INSTRUMENTATION_NAME, AsyncHttpClient18Helper.INSTANCE);

  static Instrumenter<Request, Response> instrumenter() {
    return instrumenter;
  }

  private AsyncHttpClientSingletons() {}
}
