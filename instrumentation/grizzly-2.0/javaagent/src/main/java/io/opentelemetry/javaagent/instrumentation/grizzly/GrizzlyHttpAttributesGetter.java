/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

final class GrizzlyHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpRequestPacket, HttpResponsePacket> {

  @Override
  public String method(HttpRequestPacket request) {
    return request.getMethod().getMethodString();
  }

  @Override
  public List<String> requestHeader(HttpRequestPacket request, String name) {
    String value = request.getHeader(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Nullable
  @Override
  public Long requestContentLength(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return null;
  }

  @Nullable
  @Override
  public Long requestContentLengthUncompressed(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequestPacket request, HttpResponsePacket response) {
    return response.getStatus();
  }

  @Nullable
  @Override
  public Long responseContentLength(HttpRequestPacket request, HttpResponsePacket response) {
    return null;
  }

  @Nullable
  @Override
  public Long responseContentLengthUncompressed(
      HttpRequestPacket request, HttpResponsePacket response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      HttpRequestPacket request, HttpResponsePacket response, String name) {
    String value = response.getHeader(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  public String flavor(HttpRequestPacket request) {
    String flavor = request.getProtocolString();
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Nullable
  @Override
  public String target(HttpRequestPacket request) {
    String target = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Nullable
  @Override
  public String route(HttpRequestPacket request) {
    return null;
  }

  @Override
  public String scheme(HttpRequestPacket request) {
    return request.isSecure() ? "https" : "http";
  }

  @Nullable
  @Override
  public String serverName(HttpRequestPacket request) {
    return null;
  }
}
