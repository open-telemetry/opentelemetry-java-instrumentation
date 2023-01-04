/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

public final class ApacheHttpClientEntityStorage {
  private static final VirtualField<Context, HttpRequest> httpRequestByOtelContext;
  private static final VirtualField<Context, HttpResponse> httpResponseByOtelContext;

  public ApacheHttpClientEntityStorage() {}

  static {
    httpRequestByOtelContext = VirtualField.find(Context.class, HttpRequest.class);
    httpResponseByOtelContext = VirtualField.find(Context.class, HttpResponse.class);
  }

  // http client internally makes a copy of the user request, we are storing it
  // from the interceptor, to be able to fetch actually sent headers by the client
  public static void storeHttpRequest(Context context, HttpRequest httpRequest) {
    if (httpRequest != null) {
      httpRequestByOtelContext.set(context, httpRequest);
    }
  }

  // in cases of failures (like circular redirects), callbacks may not receive the actual response
  // from the client, hence we are storing this response from interceptor to fetch attributes
  public static void storeHttpResponse(Context context, HttpResponse httpResponse) {
    if (httpResponse != null) {
      httpResponseByOtelContext.set(context, httpResponse);
    }
  }

  public static ApacheHttpClientRequest getFinalRequest(
      ApacheHttpClientRequest request, Context context) {
    HttpRequest internalRequest = httpRequestByOtelContext.get(context);
    if (internalRequest != null) {
      return request.withHttpRequest(internalRequest);
    }
    return request;
  }

  public static <T> HttpResponse getFinalResponse(T response, Context context) {
    HttpResponse internalResponse = httpResponseByOtelContext.get(context);
    if (internalResponse != null) {
      return internalResponse;
    }
    if (response instanceof HttpResponse) {
      return (HttpResponse) response;
    }
    return null;
  }

  public static void setCurrentContext(HttpContext httpContext, Context context) {
    if (httpContext != null) {
      HttpOtelContext httpOtelContext = HttpOtelContext.adapt(httpContext);
      httpOtelContext.setContext(context);
    }
  }

  public static void clearOtelAttributes(HttpContext httpContext) {
    if (httpContext != null) {
      HttpOtelContext.adapt(httpContext).clear();
    }
  }

  @Nullable
  public static Context getCurrentContext(HttpContext httpContext) {
    if (httpContext == null) {
      return null;
    }
    HttpOtelContext httpOtelContext = HttpOtelContext.adapt(httpContext);
    Context otelContext = httpOtelContext.getContext();
    if (otelContext == null) {
      // for async clients, the contexts should always be set by their instrumentation
      if (httpOtelContext.isAsyncClient()) {
        return null;
      }
      // for classic clients, context will remain same as the caller
      otelContext = currentContext();
    }
    // verifying if the current context is a http client context
    // this eliminates suppressed contexts and http processor cases which ran for
    // apache http server also present in the library
    Span span = SpanKey.HTTP_CLIENT.fromContextOrNull(otelContext);
    if (span == null) {
      return null;
    }
    return otelContext;
  }
}
