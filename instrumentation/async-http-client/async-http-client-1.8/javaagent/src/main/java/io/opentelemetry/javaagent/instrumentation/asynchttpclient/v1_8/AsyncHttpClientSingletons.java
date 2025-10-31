/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_8;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-1.8";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  public static final VirtualField<AsyncHandler<?>, AsyncHandlerData> ASYNC_HANDLER_DATA;

  static {
    AsyncHttpClientHttpAttributesGetter httpAttributesGetter =
        new AsyncHttpClientHttpAttributesGetter();

    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME,
            httpAttributesGetter,
            HttpHeaderSetter.INSTANCE);

    ASYNC_HANDLER_DATA = VirtualField.find(AsyncHandler.class, AsyncHandlerData.class);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
