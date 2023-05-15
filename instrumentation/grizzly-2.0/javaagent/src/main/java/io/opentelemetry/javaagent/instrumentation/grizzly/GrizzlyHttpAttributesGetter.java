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
  public String getMethod(HttpRequestPacket request) {
    return request.getMethod().getMethodString();
  }

  @Override
  public List<String> getRequestHeader(HttpRequestPacket request, String name) {
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
  public Integer getStatusCode(
      HttpRequestPacket request, HttpResponsePacket response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getResponseHeader(
      HttpRequestPacket request, HttpResponsePacket response, String name) {
    return toHeaderList(response.getHeaders().values(name));
  }

  @Override
  public String getScheme(HttpRequestPacket request) {
    return request.isSecure() ? "https" : "http";
  }

  @Nullable
  @Override
  public String getPath(HttpRequestPacket request) {
    return request.getRequestURI();
  }

  @Nullable
  @Override
  public String getQuery(HttpRequestPacket request) {
    return request.getQueryString();
  }
}
