/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;

final class GrizzlyHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpRequestPacket, HttpResponsePacket> {

  @Override
  public String getHttpRequestMethod(HttpRequestPacket request) {
    return request.getMethod().getMethodString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequestPacket request, String name) {
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
  public Integer getHttpResponseStatusCode(
      HttpRequestPacket request, HttpResponsePacket response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequestPacket request, HttpResponsePacket response, String name) {
    return toHeaderList(response.getHeaders().values(name));
  }

  @Override
  public String getUrlScheme(HttpRequestPacket request) {
    return request.isSecure() ? "https" : "http";
  }

  @Nullable
  @Override
  public String getUrlPath(HttpRequestPacket request) {
    return request.getRequestURI();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpRequestPacket request) {
    return request.getQueryString();
  }

  @Nullable
  @Override
  public String getNetworkTransport(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    Transport transport = request.getConnection().getTransport();
    if (transport instanceof TCPNIOTransport) {
      return "tcp";
    } else if (transport instanceof UDPNIOTransport) {
      return "udp";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getRemoteAddress();
  }

  @Override
  public Integer getNetworkPeerPort(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getLocalAddress();
  }

  @Override
  public Integer getNetworkLocalPort(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getLocalPort();
  }
}
