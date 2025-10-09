/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

enum HelidonAttributesGetter implements HttpServerAttributesGetter<ServerRequest, ServerResponse> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(ServerRequest req) {
    return req.prologue().method().text();
  }

  @Override
  public String getUrlScheme(ServerRequest req) {
    return req.requestedUri().scheme();
  }

  @Override
  public String getUrlPath(ServerRequest req) {
    return req.path().rawPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(ServerRequest req) {
    return req.query().rawValue();
  }

  @Override
  public List<String> getHttpRequestHeader(ServerRequest req, String name) {
    return req.headers().values(HeaderNames.create(name));
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ServerRequest req, ServerResponse res, @Nullable Throwable error) {
    return res.status().code();
  }

  @Override
  public List<String> getHttpResponseHeader(ServerRequest req, ServerResponse res, String name) {
    return res.headers().values(HeaderNames.create(name));
  }

  @Override
  public String getNetworkProtocolName(ServerRequest req, @Nullable ServerResponse res) {
    return req.prologue().protocol();
  }

  @Override
  public String getNetworkProtocolVersion(ServerRequest req, @Nullable ServerResponse res) {
    return req.prologue().protocolVersion();
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ServerRequest req, @Nullable ServerResponse res) {
    var address = req.remotePeer().address();
    return address instanceof InetSocketAddress s ? s : null;
  }

  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      ServerRequest req, @Nullable ServerResponse res) {
    var address = req.localPeer().address();
    return address instanceof InetSocketAddress s ? s : null;
  }
}
