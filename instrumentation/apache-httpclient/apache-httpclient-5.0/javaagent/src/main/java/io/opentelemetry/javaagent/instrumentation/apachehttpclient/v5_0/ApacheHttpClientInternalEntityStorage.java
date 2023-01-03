/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpClientInternalEntityStorage {
  private static final VirtualField<Context, HttpRequest> httpRequestByOtelContext;
  private static final VirtualField<Context, HttpResponse> httpResponseByOtelContext;

  public ApacheHttpClientInternalEntityStorage() {}

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
}
