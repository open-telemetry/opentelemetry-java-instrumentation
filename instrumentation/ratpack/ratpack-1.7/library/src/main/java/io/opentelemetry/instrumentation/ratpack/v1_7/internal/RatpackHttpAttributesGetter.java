/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.server.PublicAddress;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum RatpackHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.getMethod().getName();
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
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
  public String getUrlPath(Request request) {
    String path = request.getPath();
    return path.startsWith("/") ? path : "/" + path;
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    return request.getQuery();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.getHeaders().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getAll(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    String protocol = request.getProtocol();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    String protocol = request.getProtocol();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Override
  public Integer getNetworkPeerPort(Request request, @Nullable Response response) {
    return request.getRemoteAddress().getPort();
  }
}
