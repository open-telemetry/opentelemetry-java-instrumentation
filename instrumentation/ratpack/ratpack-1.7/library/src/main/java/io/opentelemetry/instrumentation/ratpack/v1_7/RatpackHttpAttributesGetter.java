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

  @Nullable
  @Override
  public String getServerAddress(Request request) {
    PublicAddress publicAddress = getPublicAddress(request);
    return publicAddress == null ? null : publicAddress.get().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(Request request) {
    PublicAddress publicAddress = getPublicAddress(request);
    return publicAddress == null ? null : publicAddress.get().getPort();
  }

  private static PublicAddress getPublicAddress(Request request) {
    Context ratpackContext = request.get(Context.class);
    if (ratpackContext == null) {
      return null;
    }
    return ratpackContext.get(PublicAddress.class);
  }

  @Override
  public Integer getNetworkPeerPort(Request request, @Nullable Response response) {
    return request.getRemoteAddress().getPort();
  }
}
