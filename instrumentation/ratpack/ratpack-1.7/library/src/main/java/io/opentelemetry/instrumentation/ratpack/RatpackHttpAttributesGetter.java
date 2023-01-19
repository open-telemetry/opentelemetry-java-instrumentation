/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.server.PublicAddress;

enum RatpackHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getMethod(Request request) {
    return request.getMethod().getName();
  }

  @Override
  public String getTarget(Request request) {
    // Uri is the path + query string, not a full URL
    return request.getUri();
  }

  @Override
  @Nullable
  public String getRoute(Request request) {
    // Ratpack route not available at the beginning of request.
    return null;
  }

  @Override
  @Nullable
  public String getScheme(Request request) {
    Context ratpackContext = request.get(Context.class);
    if (ratpackContext == null) {
      return null;
    }
    PublicAddress publicAddress = ratpackContext.get(PublicAddress.class);
    if (publicAddress == null) {
      return null;
    }
    return publicAddress.get().getScheme();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.getHeaders().getAll(name);
  }

  @Override
  @Nullable
  public String getFlavor(Request request) {
    switch (request.getProtocol()) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/1.1":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      default:
        // fall through
    }
    return null;
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getAll(name);
  }
}
