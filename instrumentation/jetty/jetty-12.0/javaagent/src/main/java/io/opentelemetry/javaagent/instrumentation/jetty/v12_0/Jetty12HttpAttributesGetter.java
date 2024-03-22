/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

class Jetty12HttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.getMethod();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    if (!response.isCommitted() && error != null) {
      return 500;
    }
    return response.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
    HttpURI uri = request.getHttpURI();
    return uri == null ? null : uri.getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(Request request) {
    HttpURI uri = request.getHttpURI();
    return uri == null ? null : uri.getPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    HttpURI uri = request.getHttpURI();
    return uri == null ? null : uri.getQuery();
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response unused) {
    String protocol = request.getConnectionMetaData().getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response unused) {
    String protocol = request.getConnectionMetaData().getProtocol();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      Request request, @Nullable Response unused) {
    SocketAddress address = request.getConnectionMetaData().getRemoteSocketAddress();
    return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      Request request, @Nullable Response unused) {
    SocketAddress address = request.getConnectionMetaData().getLocalSocketAddress();
    return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
  }
}
