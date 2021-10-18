/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

final class GrizzlyHttpAttributesExtractor
    extends HttpServerAttributesExtractor<HttpRequestPacket, HttpResponsePacket> {

  @Override
  protected String method(HttpRequestPacket request) {
    return request.getMethod().getMethodString();
  }

  @Override
  protected List<String> requestHeader(HttpRequestPacket request, String name) {
    String value = request.getHeader(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Nullable
  @Override
  protected Long requestContentLength(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return null;
  }

  @Nullable
  @Override
  protected Long requestContentLengthUncompressed(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequestPacket request, HttpResponsePacket response) {
    return response.getStatus();
  }

  @Nullable
  @Override
  protected Long responseContentLength(HttpRequestPacket request, HttpResponsePacket response) {
    return null;
  }

  @Nullable
  @Override
  protected Long responseContentLengthUncompressed(
      HttpRequestPacket request, HttpResponsePacket response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequestPacket request, HttpResponsePacket response, String name) {
    String value = response.getHeader(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  protected String flavor(HttpRequestPacket request) {
    String flavor = request.getProtocolString();
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Nullable
  @Override
  protected String target(HttpRequestPacket request) {
    String target = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Nullable
  @Override
  protected String route(HttpRequestPacket request) {
    return null;
  }

  @Override
  protected String scheme(HttpRequestPacket request) {
    return request.isSecure() ? "https" : "http";
  }

  @Nullable
  @Override
  protected String serverName(HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return null;
  }
}
