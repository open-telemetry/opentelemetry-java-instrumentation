/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
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
  public String getPath(Request request) {
    String path = request.getPath();
    return path.startsWith("/") ? path : "/" + path;
  }

  @Nullable
  @Override
  public String getQuery(Request request) {
    return request.getQuery();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.getHeaders().getAll(name);
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
