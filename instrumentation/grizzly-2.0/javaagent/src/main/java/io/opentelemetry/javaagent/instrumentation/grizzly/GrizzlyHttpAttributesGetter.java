/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.ArrayList;
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
    return toHeaderList(request.getHeaders().values(name));
  }

  private static List<String> toHeaderList(Iterable<String> values) {
    if (values.iterator().hasNext()) {
      List<String> result = new ArrayList<>();
      values.forEach(result::add);
      return result;
    }
    return emptyList();
  }

  @Override
  public Integer statusCode(
      HttpRequestPacket request, HttpResponsePacket response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> responseHeader(
      HttpRequestPacket request, HttpResponsePacket response, String name) {
    return toHeaderList(response.getHeaders().values(name));
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
