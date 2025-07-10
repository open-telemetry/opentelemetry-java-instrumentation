/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-2.0";

  private static final Instrumenter<RequestContext, Response> INSTRUMENTER;
  public static final VirtualField<AsyncHandler<?>, RequestContext> ASYNC_HANDLER_REQUEST_CONTEXT;
  public static final VirtualField<Request, Context> REQUEST_CONTEXT;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME,
            new AsyncHttpClientHttpAttributesGetter(),
            HttpHeaderSetter.INSTANCE);

    ASYNC_HANDLER_REQUEST_CONTEXT = VirtualField.find(AsyncHandler.class, RequestContext.class);
    REQUEST_CONTEXT = VirtualField.find(Request.class, Context.class);
  }

  public static Instrumenter<RequestContext, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
